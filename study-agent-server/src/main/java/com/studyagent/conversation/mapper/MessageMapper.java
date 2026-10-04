package com.studyagent.conversation.mapper;

import com.studyagent.conversation.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {
    List<MessageEntity> findBefore(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                                    @Param("beforeId") Long beforeId, @Param("limit") int limit);
}
