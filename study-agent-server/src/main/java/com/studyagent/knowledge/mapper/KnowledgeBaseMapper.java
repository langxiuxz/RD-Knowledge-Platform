package com.studyagent.knowledge.mapper;
import com.studyagent.knowledge.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface KnowledgeBaseMapper {
 int insert(KnowledgeBaseEntity entity);
 KnowledgeBaseEntity findById(@Param("id") Long id);
 KnowledgeBaseEntity lockById(@Param("id") Long id);
 Integer currentSnapshotNo(@Param("id") Long id);
 long count(@Param("projectId") Long projectId,@Param("name") String name,@Param("status") String status);
 long countByUserId(@Param("userId") Long userId,@Param("name") String name,@Param("status") String status);
 List<KnowledgeBaseEntity> findPage(@Param("projectId") Long projectId,@Param("name") String name,@Param("status") String status,@Param("offset") long offset,@Param("pageSize") int pageSize);
 int update(KnowledgeBaseEntity entity);
 int archive(@Param("id") Long id);
 int updateSnapshot(@Param("id") Long id,@Param("snapshotId") Long snapshotId);
}
