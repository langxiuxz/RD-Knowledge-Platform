package com.studyagent.conversation.mapper;

import com.studyagent.conversation.entity.MessageCitationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageCitationMapper {
    int insertBatch(@Param("rows") List<MessageCitationEntity> rows);
    List<MessageCitationEntity> findByMessageId(@Param("messageId") Long messageId);
}
