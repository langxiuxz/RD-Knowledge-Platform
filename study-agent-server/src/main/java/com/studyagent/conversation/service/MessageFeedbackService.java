package com.studyagent.conversation.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.conversation.entity.MessageEntity;
import com.studyagent.conversation.entity.MessageFeedbackEntity;
import com.studyagent.conversation.mapper.MessageFeedbackMapper;
import com.studyagent.conversation.mapper.MessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageFeedbackService {
    private final MessageMapper messageMapper;
    private final MessageFeedbackMapper feedbackMapper;

    public MessageFeedbackService(MessageMapper messageMapper, MessageFeedbackMapper feedbackMapper) {
        this.messageMapper = messageMapper;
        this.feedbackMapper = feedbackMapper;
    }

    @Transactional
    public void save(Long userId, Long messageId, Integer rating, String reason) {
        if (rating == null || (rating != 1 && rating != -1)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "rating只能是1或-1");
        }
        MessageEntity message = messageMapper.findByIdAndOwner(messageId, userId);
        if (message == null) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        if (!"assistant".equalsIgnoreCase(message.getRole())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只能反馈AI消息");
        }
        MessageFeedbackEntity feedback = new MessageFeedbackEntity();
        feedback.setMessageId(messageId);
        feedback.setUserId(userId);
        feedback.setRating(rating);
        feedback.setReason(reason);
        feedbackMapper.upsert(feedback);
    }
}
