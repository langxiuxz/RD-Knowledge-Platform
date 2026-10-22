package com.studyagent.knowledge.vo;
import com.studyagent.knowledge.entity.KnowledgeBaseEntity;
import java.time.LocalDateTime;
public record KnowledgeBaseVO(Long id, Long projectId, String name, String description, String status, Long documentCount,
                              Long currentSnapshotId, Integer currentSnapshotNo, LocalDateTime updateTime) {
    public static KnowledgeBaseVO from(KnowledgeBaseEntity e) { return new KnowledgeBaseVO(e.getId(),e.getProjectId(),e.getName(),
      e.getDescription(),e.getStatus(),e.getDocumentCount(),e.getCurrentSnapshotId(),e.getCurrentSnapshotNo(),e.getUpdateTime()); }
}
