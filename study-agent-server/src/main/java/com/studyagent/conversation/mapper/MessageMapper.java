package com.studyagent.conversation.mapper;

import com.studyagent.conversation.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {
    int insert(MessageEntity entity);
    List<MessageEntity> findBefore(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                                    @Param("beforeId") Long beforeId, @Param("limit") int limit);
    int bindTrace(@Param("id") Long id, @Param("userId") Long userId, @Param("traceId") String traceId);
    int complete(@Param("id") Long id, @Param("userId") Long userId, @Param("content") String content,
                 @Param("traceId") String traceId, @Param("finishReason") String finishReason);
    int fail(@Param("id") Long id, @Param("userId") Long userId, @Param("content") String content,
             @Param("traceId") String traceId, @Param("errorMessage") String errorMessage);
    int cancelByTrace(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                      @Param("traceId") String traceId);
    MessageEntity findByTraceAndOwner(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                                      @Param("traceId") String traceId);
}
