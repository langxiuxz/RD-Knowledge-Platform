package com.studyagent.document.mapper;
import com.studyagent.document.entity.DocumentEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface DocumentMapper {
 int insert(DocumentEntity entity);
 DocumentEntity findById(@Param("id") Long id);
 DocumentEntity findByIdForUpdate(@Param("id") Long id);
 long count(@Param("knowledgeBaseId") Long knowledgeBaseId,@Param("keyword") String keyword,@Param("status") String status,@Param("learner") boolean learner);
 List<DocumentEntity> findPage(@Param("knowledgeBaseId") Long knowledgeBaseId,@Param("keyword") String keyword,@Param("status") String status,@Param("offset") long offset,@Param("pageSize") int pageSize,@Param("learner") boolean learner);
 int publish(@Param("id") Long id,@Param("versionId") Long versionId);
 int archive(@Param("id") Long id);
 int touch(@Param("id") Long id);
}
