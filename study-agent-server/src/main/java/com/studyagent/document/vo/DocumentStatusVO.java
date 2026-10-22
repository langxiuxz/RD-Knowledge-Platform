package com.studyagent.document.vo;
import com.studyagent.document.entity.DocumentVersionEntity;
public record DocumentStatusVO(String processStatus, String publishStatus, Integer progress, Integer chunkCount, String errorMessage, String taskId) {
 public static DocumentStatusVO from(DocumentVersionEntity e) { return new DocumentStatusVO(e.getProcessStatus(),e.getPublishStatus(),
 e.getProgress(),e.getChunkCount(),e.getErrorMessage(),e.getCurrentTaskId()); }
}
