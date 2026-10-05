package com.studyagent.conversation.mapper;

import com.studyagent.conversation.entity.MessageFeedbackEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageFeedbackMapper {
    int upsert(MessageFeedbackEntity entity);
}
