package com.studyagent.document.mapper;

import com.studyagent.document.entity.DocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DocumentMapper {
    int failStaleTasks(@Param("timeoutSeconds") long timeoutSeconds, @Param("batchSize") int batchSize,
                       @Param("errorMessage") String errorMessage);
    int insert(DocumentEntity entity);
    DocumentEntity findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    DocumentEntity findById(@Param("id") Long id);
    DocumentEntity findByIdForUpdate(@Param("id") Long id);
    long countByKnowledgeBaseAndUserId(@Param("knowledgeBaseId") Long knowledgeBaseId, @Param("userId") Long userId,
                                        @Param("fileName") String fileName, @Param("status") String status);
    List<DocumentEntity> findPageByKnowledgeBaseAndUserId(@Param("knowledgeBaseId") Long knowledgeBaseId, @Param("userId") Long userId,
                                                           @Param("fileName") String fileName, @Param("status") String status,
                                                           @Param("offset") int offset, @Param("pageSize") int pageSize);
    int updateTaskStart(DocumentEntity entity);
    int updateProgressIfCurrentTask(@Param("id") Long id, @Param("taskId") String taskId, @Param("status") String status,
                                    @Param("progress") Integer progress, @Param("chunkCount") Integer chunkCount,
                                    @Param("errorMessage") String errorMessage, @Param("ready") boolean ready);
    List<String> findStorageKeysByKnowledgeBaseAndUser(@Param("knowledgeBaseId") Long knowledgeBaseId, @Param("userId") Long userId);
    boolean existsByStorageKey(@Param("storageKey") String storageKey);
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
