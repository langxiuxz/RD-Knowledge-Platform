package com.studyagent.document.entity;

import java.time.LocalDateTime;

public class DocumentVersionEntity {
    private Long id;
    private Long documentId;
    private Long projectId;
    private Long knowledgeBaseId;
    private Integer versionNo;
    private String fileName;
    private String fileType;
    private String mimeType;
    private Long fileSize;
    private String contentHash;
    private String storageKey;
    private String processStatus;
    private String publishStatus;
    private Integer progress;
    private Integer chunkCount;
    private String currentTaskId;
    private String changeNote;
    private String errorMessage;
    private Long reviewerId;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long value) { documentId=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long value) { knowledgeBaseId=value; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer value) { versionNo=value; }
    public String getFileName() { return fileName; }
    public void setFileName(String value) { fileName=value; }
    public String getFileType() { return fileType; }
    public void setFileType(String value) { fileType=value; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String value) { mimeType=value; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { fileSize=value; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String value) { contentHash=value; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String value) { storageKey=value; }
    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String value) { processStatus=value; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String value) { publishStatus=value; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer value) { progress=value; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer value) { chunkCount=value; }
    public String getCurrentTaskId() { return currentTaskId; }
    public void setCurrentTaskId(String value) { currentTaskId=value; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String value) { changeNote=value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage=value; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long value) { reviewerId=value; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String value) { reviewComment=value; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime value) { reviewedAt=value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt=value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime=value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime=value; }
}
