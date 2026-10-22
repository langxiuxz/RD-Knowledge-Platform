package com.studyagent.knowledge.mapper;
import com.studyagent.knowledge.entity.KnowledgeSnapshotEntity;
import com.studyagent.knowledge.vo.KnowledgeSnapshotVOs;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface KnowledgeSnapshotMapper {
 int latestNo(@Param("knowledgeBaseId") Long knowledgeBaseId);
 List<KnowledgeSnapshotVOs.Item> currentPublishedItems(@Param("knowledgeBaseId") Long knowledgeBaseId);
 int insert(KnowledgeSnapshotEntity entity);
 int insertItems(@Param("snapshotId") Long snapshotId,@Param("items") List<KnowledgeSnapshotVOs.Item> items);
 long countByKnowledgeBase(@Param("knowledgeBaseId") Long knowledgeBaseId);
 List<KnowledgeSnapshotEntity> findPageByKnowledgeBase(@Param("knowledgeBaseId") Long knowledgeBaseId,
         @Param("offset") long offset,@Param("pageSize") int pageSize);
 KnowledgeSnapshotEntity find(@Param("id") Long id);
 List<KnowledgeSnapshotVOs.Item> findItems(@Param("id") Long id);
 List<Long> findVersionIds(@Param("snapshotId") Long snapshotId,@Param("projectId") Long projectId,@Param("knowledgeBaseId") Long knowledgeBaseId);
}
