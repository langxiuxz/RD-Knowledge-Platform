package com.studyagent.conversation;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.conversation.entity.ConversationEntity;
import com.studyagent.conversation.mapper.ConversationMapper;
import com.studyagent.conversation.mapper.MessageCitationMapper;
import com.studyagent.conversation.mapper.MessageMapper;
import com.studyagent.conversation.service.ConversationService;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
    @Mock ConversationMapper conversationMapper;
    @Mock MessageMapper messageMapper;
    @Mock MessageCitationMapper citationMapper;
    @Mock KnowledgeBaseService knowledgeBaseService;

    @Test
    void get_whenOwnedByAnotherUser_shouldReject() {
        ConversationService service = new ConversationService(
                conversationMapper, messageMapper, citationMapper, knowledgeBaseService);
        when(conversationMapper.findByIdAndUserId(30L, 2L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(2L, 30L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    @Test
    void messages_shouldVerifyConversationOwnershipBeforeQueryingMessages() {
        ConversationService service = new ConversationService(
                conversationMapper, messageMapper, citationMapper, knowledgeBaseService);
        ConversationEntity owned = new ConversationEntity();
        owned.setId(30L);
        owned.setUserId(1L);
        when(conversationMapper.findByIdAndUserId(30L, 1L)).thenReturn(owned);
        when(messageMapper.findBefore(30L, 1L, null, 21)).thenReturn(java.util.List.of());

        service.messages(1L, 30L, null, 20);

        verify(messageMapper).findBefore(30L, 1L, null, 21);
    }
}
