package com.studyagent.document.mapper;
import com.studyagent.document.entity.DocumentVersionEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface DocumentVersionMapper {
 int insert(DocumentVersionEntity entity);
 DocumentVersionEntity findById(@Param("id") Long id);
 DocumentVersionEntity findByIdForUpdate(@Param("id") Long id);
 DocumentVersionEntity latestForUpdate(@Param("documentId") Long documentId);
 List<DocumentVersionEntity> findByDocumentId(@Param("documentId") Long documentId);
 int startTask(@Param("id") Long id,@Param("expectedTaskId") String expectedTaskId,@Param("taskId") String taskId,@Param("contentHash") String contentHash);
 int updateProgress(@Param("id") Long id,@Param("taskId") String taskId,@Param("status") String status,@Param("progress") Integer progress,
  @Param("chunkCount") Integer chunkCount,@Param("errorMessage") String errorMessage,@Param("contentHash") String contentHash,@Param("ready") boolean ready);
 int submitReview(@Param("id") Long id);
 int review(@Param("id") Long id,@Param("decision") String decision,@Param("reviewerId") Long reviewerId,@Param("comment") String comment);
 int publish(@Param("id") Long id);
 int supersede(@Param("id") Long id);
 int markDependencies(@Param("versionId") Long versionId);
 boolean existsByStorageKey(@Param("storageKey") String storageKey);
 int failStaleTasks(@Param("timeoutSeconds") long timeoutSeconds,@Param("batchSize") int batchSize,@Param("errorMessage") String errorMessage);
}
