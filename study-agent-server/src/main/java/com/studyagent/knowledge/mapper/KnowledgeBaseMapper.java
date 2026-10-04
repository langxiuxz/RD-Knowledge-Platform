package com.studyagent.knowledge.mapper;

import com.studyagent.knowledge.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {
    int insert(KnowledgeBaseEntity entity);
    KnowledgeBaseEntity findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    long countByUserId(@Param("userId") Long userId, @Param("name") String name, @Param("status") String status);
    List<KnowledgeBaseEntity> findPageByUserId(@Param("userId") Long userId, @Param("name") String name,
                                                 @Param("status") String status, @Param("offset") int offset,
                                                 @Param("pageSize") int pageSize);
    int updateByIdAndUserId(KnowledgeBaseEntity entity);
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
