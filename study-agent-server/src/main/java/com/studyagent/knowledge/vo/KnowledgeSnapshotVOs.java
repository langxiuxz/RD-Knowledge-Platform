package com.studyagent.knowledge.vo;
import java.time.LocalDateTime;
import java.util.List;
public final class KnowledgeSnapshotVOs {
 private KnowledgeSnapshotVOs() {}
 public record Item(Long documentId,Long documentVersionId,Integer versionNo,String title) {}
 public record Summary(Long id,Integer snapshotNo,Long itemCount,Long createdBy,LocalDateTime createTime,String reason) {}
 public record Detail(Long id,Long projectId,Long knowledgeBaseId,Integer snapshotNo,String reason,List<Item> items) {}
 public record RagScope(Long projectId,Long knowledgeBaseId,Long knowledgeSnapshotId,List<Long> allowedDocumentVersionIds) {}
 public record Publication(Long documentId,Long versionId,String publishStatus,Long snapshotId,Integer snapshotNo,LocalDateTime publishedAt) {}
}
