package com.studyagent.conversation.mapper;

import com.studyagent.conversation.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationMapper {
    int insert(ConversationEntity entity);
    ConversationEntity findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    long countByUserId(@Param("userId") Long userId, @Param("keyword") String keyword, @Param("mode") String mode);
    List<ConversationEntity> findPageByUserId(@Param("userId") Long userId, @Param("keyword") String keyword,
                                                @Param("mode") String mode, @Param("offset") int offset,
                                                @Param("pageSize") int pageSize);
    int updateTitleByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId,
                                 @Param("title") String title);
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
