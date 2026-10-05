package com.studyagent.internal.service;

import com.studyagent.conversation.entity.MessageEntity;
import com.studyagent.conversation.mapper.MessageMapper;
import com.studyagent.internal.dto.MemoryCandidateRequest;
import com.studyagent.internal.dto.MemoryQueryRequest;
import com.studyagent.internal.entity.LongTermMemoryEntity;
import com.studyagent.internal.mapper.LongTermMemoryMapper;
import com.studyagent.internal.vo.LongTermMemoryToolVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class InternalMemoryService {
    private static final Set<String> CATEGORIES = Set.of("LEARNING_GOAL", "EXPLANATION_PREFERENCE", "LEARNING_STYLE");
    private static final Pattern EXPLICIT = Pattern.compile("(以后|今后|从现在起|请记住|记住|我的学习目标|我想系统学习|我希望长期学习|我喜欢|我更喜欢)");
    private static final Pattern FACT = Pattern.compile("(?i)(mastery|correct\\s*rate|quiz|测验|错题|掌握度|正确率|学习记录|progress|分数)");
    private final LongTermMemoryMapper memoryMapper;
    private final MessageMapper messageMapper;

    public InternalMemoryService(LongTermMemoryMapper memoryMapper, MessageMapper messageMapper) {
        this.memoryMapper = memoryMapper;
        this.messageMapper = messageMapper;
    }

    public boolean storeCandidate(MemoryCandidateRequest request) {
        String evidence = request.evidenceText();
        boolean categoryEvidence = switch (request.category()) {
            case "EXPLANATION_PREFERENCE" -> evidence.contains("解释")
                    && (evidence.contains("以后") || evidence.contains("今后") || evidence.contains("从现在起") || evidence.contains("请记住") || evidence.contains("记住"));
            case "LEARNING_GOAL" -> evidence.contains("学习目标") || evidence.contains("我想学");
            case "LEARNING_STYLE" -> evidence.contains("我喜欢") || evidence.contains("我更喜欢")
                    || evidence.contains("请记住") || evidence.contains("以后") || evidence.contains("今后") || evidence.contains("从现在起");
            default -> false;
        };
        if (!CATEGORIES.contains(request.category()) || FACT.matcher(evidence).find()
                || !EXPLICIT.matcher(evidence).find() || !categoryEvidence
                || !request.value().trim().equals(evidence.trim())) return false;
        MessageEntity source = messageMapper.findByIdConversationAndOwner(
                request.messageId(), request.conversationId(), request.userId());
        if (source == null || !"user".equals(source.getRole()) || !"COMPLETED".equals(source.getStatus())
                || source.getContent() == null || !source.getContent().contains(request.evidenceText())) return false;
        LongTermMemoryEntity memory = new LongTermMemoryEntity();
        memory.setUserId(request.userId());
        memory.setCategory(request.category());
        memory.setMemoryKey(request.memoryKey());
        memory.setMemoryValue(request.value().trim());
        memory.setSourceMessageId(source.getId());
        return memoryMapper.upsert(memory) > 0;
    }

    public List<LongTermMemoryToolVO> memories(MemoryQueryRequest request) {
        return memoryMapper.findRecentByUserId(request.userId(), Math.min(request.safeLimit(), 20)).stream()
                .map(memory -> new LongTermMemoryToolVO(memory.getCategory(), memory.getMemoryKey(),
                        memory.getMemoryValue(), memory.getUpdateTime())).toList();
    }
}
