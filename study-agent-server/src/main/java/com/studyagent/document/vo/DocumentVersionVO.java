package com.studyagent.document.vo;
import com.studyagent.document.entity.DocumentVersionEntity;
import java.time.LocalDateTime;
public record DocumentVersionVO(Long id, Long documentId, Integer versionNo, String fileName, Long fileSize, String contentHash,
 String processStatus, Integer progress, Integer chunkCount, String publishStatus, String changeNote,
 Long reviewer, String reviewComment, LocalDateTime publishedAt, String errorMessage) {
 public static DocumentVersionVO from(DocumentVersionEntity e) { return new DocumentVersionVO(e.getId(),e.getDocumentId(),e.getVersionNo(),
 e.getFileName(),e.getFileSize(),e.getContentHash(),e.getProcessStatus(),e.getProgress(),e.getChunkCount(),e.getPublishStatus(),e.getChangeNote(),
 e.getReviewerId(),e.getReviewComment(),e.getPublishedAt(),e.getErrorMessage()); }
}
