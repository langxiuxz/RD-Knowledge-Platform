package com.studyagent.conversation.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.conversation.entity.MessageCitationEntity;
import com.studyagent.conversation.entity.MessageEntity;
import com.studyagent.conversation.mapper.ConversationMapper;
import com.studyagent.conversation.mapper.MessageCitationMapper;
import com.studyagent.conversation.mapper.MessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class MessagePersistenceService {
    private final MessageMapper messageMapper;
    private final MessageCitationMapper citationMapper;
    private final ConversationMapper conversationMapper;

    public MessagePersistenceService(MessageMapper messageMapper, MessageCitationMapper citationMapper,
                                     ConversationMapper conversationMapper) {
        this.messageMapper = messageMapper;
        this.citationMapper = citationMapper;
        this.conversationMapper = conversationMapper;
    }

    @Transactional
    public MessageExchange createExchange(Long userId, Long conversationId, String content) {
        MessageEntity userMessage = message(conversationId, userId, "user", content, "COMPLETED");
        messageMapper.insert(userMessage);
        MessageEntity assistantMessage = message(conversationId, userId, "assistant", "", "GENERATING");
        messageMapper.insert(assistantMessage);
        conversationMapper.touch(conversationId, userId);
        return new MessageExchange(userMessage.getId(), assistantMessage.getId());
    }

    public void bindTrace(Long assistantMessageId, Long userId, String traceId) {
        messageMapper.bindTrace(assistantMessageId, userId, traceId);
    }

    @Transactional
    public void complete(Long assistantMessageId, Long userId, String content, String traceId,
                         String finishReason, List<AiClient.Citation> citations) {
        int updated = messageMapper.complete(assistantMessageId, userId, content, traceId, finishReason);
        if (updated == 0) return;
        if (citations == null || citations.isEmpty()) return;
        List<MessageCitationEntity> rows = new ArrayList<>(citations.size());
        for (int index = 0; index < citations.size(); index++) {
            AiClient.Citation source = citations.get(index);
            MessageCitationEntity row = new MessageCitationEntity();
            row.setMessageId(assistantMessageId);
            row.setDocumentId(source.documentId());
            row.setDocumentVersionId(source.documentVersionId());
            row.setKnowledgeSnapshotId(source.knowledgeSnapshotId());
            row.setChunkId(source.chunkId());
            row.setFileName(source.fileName());
            row.setPage(source.page());
            row.setScore(source.score() == null ? null : BigDecimal.valueOf(source.score()));
            row.setSortOrder(index);
            rows.add(row);
        }
        citationMapper.insertBatch(rows);
    }

    public void fail(Long assistantMessageId, Long userId, String partialContent, String traceId, String errorMessage) {
        messageMapper.fail(assistantMessageId, userId, partialContent, traceId, truncate(errorMessage));
    }

    public void cancel(Long conversationId, Long userId, String traceId) {
        messageMapper.cancelByTrace(conversationId, userId, traceId);
    }

    public MessageEntity findByTrace(Long conversationId, Long userId, String traceId) {
        return messageMapper.findByTraceAndOwner(conversationId, userId, traceId);
    }

    private MessageEntity message(Long conversationId, Long userId, String role, String content, String status) {
        MessageEntity entity = new MessageEntity();
        entity.setConversationId(conversationId);
        entity.setUserId(userId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setStatus(status);
        return entity;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "AI service error";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public record MessageExchange(Long userMessageId, Long assistantMessageId) {}
}
