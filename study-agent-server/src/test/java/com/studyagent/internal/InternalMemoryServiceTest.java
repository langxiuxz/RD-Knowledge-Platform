package com.studyagent.internal;

import com.studyagent.conversation.entity.MessageEntity;
import com.studyagent.conversation.mapper.MessageMapper;
import com.studyagent.internal.dto.MemoryCandidateRequest;
import com.studyagent.internal.mapper.LongTermMemoryMapper;
import com.studyagent.internal.service.InternalMemoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InternalMemoryServiceTest {
    private final LongTermMemoryMapper memoryMapper = mock(LongTermMemoryMapper.class);
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final InternalMemoryService service = new InternalMemoryService(memoryMapper, messageMapper);

    @Test
    void rejectsBusinessFactsBeforePersistingMemory() {
        MemoryCandidateRequest request = new MemoryCandidateRequest(7L, 301L, 9001L,
                "EXPLANATION_PREFERENCE", "explanation_preference",
                "以后解释时请记住我的 mastery 是 80", "以后解释时请记住我的 mastery 是 80");

        assertThat(service.storeCandidate(request)).isFalse();
        verifyNoInteractions(memoryMapper, messageMapper);
    }

    @Test
    void storesOnlyPreferenceSupportedByOwnedCompletedUserMessage() {
        String evidence = "以后解释 Java 多给代码例子";
        MessageEntity source = new MessageEntity();
        source.setId(9001L);
        source.setRole("user");
        source.setStatus("COMPLETED");
        source.setContent(evidence);
        when(messageMapper.findByIdConversationAndOwner(9001L, 301L, 7L)).thenReturn(source);
        when(memoryMapper.upsert(any())).thenReturn(1);
        MemoryCandidateRequest request = new MemoryCandidateRequest(7L, 301L, 9001L,
                "EXPLANATION_PREFERENCE", "explanation_preference", evidence, evidence);

        assertThat(service.storeCandidate(request)).isTrue();
        ArgumentCaptor<com.studyagent.internal.entity.LongTermMemoryEntity> captor =
                ArgumentCaptor.forClass(com.studyagent.internal.entity.LongTermMemoryEntity.class);
        verify(memoryMapper).upsert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getSourceMessageId()).isEqualTo(9001L);
        assertThat(captor.getValue().getMemoryValue()).isEqualTo(evidence);
    }

    @Test
    void refusesCandidateWhenOwnedSourceMessageCannotBeVerified() {
        String evidence = "以后解释 Java 多给代码例子";
        when(messageMapper.findByIdConversationAndOwner(9001L, 301L, 7L)).thenReturn(null);
        MemoryCandidateRequest request = new MemoryCandidateRequest(7L, 301L, 9001L,
                "EXPLANATION_PREFERENCE", "explanation_preference", evidence, evidence);

        assertThat(service.storeCandidate(request)).isFalse();
        verifyNoInteractions(memoryMapper);
    }
}
