package com.studyagent.conversation.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.conversation.dto.ConversationQuery;
import com.studyagent.conversation.dto.CreateConversationRequest;
import com.studyagent.conversation.dto.UpdateConversationRequest;
import com.studyagent.conversation.entity.ConversationEntity;
import com.studyagent.conversation.entity.MessageCitationEntity;
import com.studyagent.conversation.entity.MessageEntity;
import com.studyagent.conversation.mapper.ConversationMapper;
import com.studyagent.conversation.mapper.MessageCitationMapper;
import com.studyagent.conversation.mapper.MessageMapper;
import com.studyagent.conversation.vo.ConversationIdVO;
import com.studyagent.conversation.vo.ConversationVO;
import com.studyagent.conversation.vo.MessageCitationVO;
import com.studyagent.conversation.vo.MessagePageVO;
import com.studyagent.conversation.vo.MessageVO;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ConversationService {
    private static final Set<String> MODES = Set.of("CHAT", "RAG", "AGENT");
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final MessageCitationMapper citationMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    public ConversationService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                               MessageCitationMapper citationMapper,
                               KnowledgeBaseService knowledgeBaseService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.citationMapper = citationMapper;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Transactional
    public ConversationIdVO create(Long userId, CreateConversationRequest request) {
        if (!MODES.contains(request.mode())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "mode不合法");
        if (request.knowledgeBaseId() != null) knowledgeBaseService.requireOwned(userId, request.knowledgeBaseId());
        ConversationEntity entity = new ConversationEntity();
        entity.setUserId(userId);
        entity.setTitle(request.title());
        entity.setKnowledgeBaseId(request.knowledgeBaseId());
        entity.setMode(request.mode());
        conversationMapper.insert(entity);
        return new ConversationIdVO(entity.getId());
    }

    public PageResult<ConversationVO> list(Long userId, ConversationQuery query) {
        if (query.mode() != null && !MODES.contains(query.mode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "mode不合法");
        }
        long total = conversationMapper.countByUserId(userId, query.keyword(), query.mode());
        List<ConversationVO> rows = conversationMapper.findPageByUserId(userId, query.keyword(), query.mode(),
                        (query.page() - 1) * query.pageSize(), query.pageSize())
                .stream().map(this::toVO).toList();
        return new PageResult<>(total, rows);
    }

    public ConversationVO get(Long userId, Long id) { return toVO(requireOwned(userId, id)); }

    @Transactional
    public void update(Long userId, Long id, UpdateConversationRequest request) {
        requireOwned(userId, id);
        conversationMapper.updateTitleByIdAndUserId(id, userId, request.title());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        conversationMapper.deleteByIdAndUserId(id, userId);
    }

    public MessagePageVO messages(Long userId, Long conversationId, Long beforeId, int pageSize) {
        requireOwned(userId, conversationId);
        List<MessageEntity> loaded = messageMapper.findBefore(conversationId, userId, beforeId, pageSize + 1);
        boolean hasMore = loaded.size() > pageSize;
        List<MessageEntity> visible = hasMore ? loaded.subList(0, pageSize) : loaded;
        List<MessageVO> rows = new ArrayList<>(visible.size());
        for (MessageEntity message : visible) {
            List<MessageCitationVO> citations = "assistant".equals(message.getRole())
                    ? citationMapper.findByMessageId(message.getId()).stream().map(this::toCitationVO).toList()
                    : List.of();
            rows.add(new MessageVO(message.getId(), message.getRole(), message.getContent(), citations));
        }
        return new MessagePageVO(rows, hasMore);
    }

    public ConversationEntity requireOwned(Long userId, Long id) {
        ConversationEntity entity = conversationMapper.findByIdAndUserId(id, userId);
        if (entity == null) throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        return entity;
    }

    private ConversationVO toVO(ConversationEntity entity) {
        return new ConversationVO(entity.getId(), entity.getTitle(), entity.getKnowledgeBaseId(), entity.getMode(),
                entity.getMessageCount() == null ? 0L : entity.getMessageCount());
    }

    private MessageCitationVO toCitationVO(MessageCitationEntity entity) {
        return new MessageCitationVO(entity.getDocumentId(), entity.getChunkId(), entity.getFileName(),
                entity.getPage(), entity.getScore() == null ? null : entity.getScore().doubleValue());
    }
}
