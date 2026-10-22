package com.studyagent.conversation;

import com.studyagent.ai.client.AiClient;
import com.studyagent.conversation.mapper.ConversationMapper;
import com.studyagent.conversation.mapper.MessageCitationMapper;
import com.studyagent.conversation.mapper.MessageMapper;
import com.studyagent.conversation.service.MessagePersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagePersistenceServiceTest {
    @Mock MessageMapper messageMapper;
    @Mock MessageCitationMapper citationMapper;
    @Mock ConversationMapper conversationMapper;

    @Test
    void complete_whenMessageWasCancelled_shouldNotInsertCitations() {
        MessagePersistenceService service = new MessagePersistenceService(
                messageMapper, citationMapper, conversationMapper);
        when(messageMapper.complete(9001L, 1L, "answer", "trace-1", "stop")).thenReturn(0);

        service.complete(9001L, 1L, "answer", "trace-1", "stop",
                List.of(new AiClient.Citation("chunk-1", 101L, "notes.pdf", 1, 0.9, 1001L, 501L)));

        verify(citationMapper, never()).insertBatch(anyList());
    }
}
