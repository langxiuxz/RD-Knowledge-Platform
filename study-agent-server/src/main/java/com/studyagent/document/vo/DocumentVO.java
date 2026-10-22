package com.studyagent.document.vo;
import com.studyagent.document.entity.DocumentEntity;
import java.time.LocalDateTime;
public record DocumentVO(Long id, String title, String status, Long currentPublishedVersionId, Integer currentVersionNo,
                         String latestVersionProcessStatus, String latestVersionPublishStatus, LocalDateTime updateTime) {
 public static DocumentVO from(DocumentEntity e) { return new DocumentVO(e.getId(),e.getTitle(),e.getStatus(),e.getCurrentPublishedVersionId(),
 e.getCurrentVersionNo(),e.getLatestVersionProcessStatus(),e.getLatestVersionPublishStatus(),e.getUpdateTime()); }
}
