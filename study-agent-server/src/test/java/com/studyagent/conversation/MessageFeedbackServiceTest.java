package com.studyagent.conversation;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.conversation.entity.MessageEntity;
import com.studyagent.conversation.entity.MessageFeedbackEntity;
import com.studyagent.conversation.mapper.MessageFeedbackMapper;
import com.studyagent.conversation.mapper.MessageMapper;
import com.studyagent.conversation.service.MessageFeedbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageFeedbackServiceTest {
    @Mock MessageMapper messageMapper;
    @Mock MessageFeedbackMapper feedbackMapper;
    @InjectMocks MessageFeedbackService service;

    @Test
    void savesFeedbackOnlyForOwnedAssistantMessage() {
        MessageEntity message = new MessageEntity();
        message.setRole("assistant");
        when(messageMapper.findByIdAndOwner(9L, 3L)).thenReturn(message);

        service.save(3L, 9L, 1, "清楚");

        ArgumentCaptor<MessageFeedbackEntity> captor = ArgumentCaptor.forClass(MessageFeedbackEntity.class);
        verify(feedbackMapper).upsert(captor.capture());
        assertEquals(3L, captor.getValue().getUserId());
        assertEquals(9L, captor.getValue().getMessageId());
        assertEquals(1, captor.getValue().getRating());
    }

    @Test
    void rejectsMissingOrNonAssistantMessages() {
        when(messageMapper.findByIdAndOwner(10L, 3L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.save(3L, 10L, 1, null));
        MessageEntity userMessage = new MessageEntity();
        userMessage.setRole("user");
        when(messageMapper.findByIdAndOwner(11L, 3L)).thenReturn(userMessage);
        assertThrows(BusinessException.class, () -> service.save(3L, 11L, -1, null));
        verifyNoInteractions(feedbackMapper);
    }

    @Test
    void rejectsUnsupportedRating() {
        assertThrows(BusinessException.class, () -> service.save(3L, 9L, 0, null));
        verifyNoInteractions(messageMapper, feedbackMapper);
    }
}
