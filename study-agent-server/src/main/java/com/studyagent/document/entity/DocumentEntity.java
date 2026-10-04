package com.studyagent.document.entity;

import java.time.LocalDateTime;

public class DocumentEntity {
    private Long id;
    private Long knowledgeBaseId;
    private Long userId;
    private String fileName;
    private String fileType;
    private String mimeType;
    private Long fileSize;
    private String storageKey;
    private String fileUrl;
    private String status;
    private Integer progress;
    private Integer chunkCount;
    private String currentTaskId;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime processedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getKnowledgeBaseId(){return knowledgeBaseId;} public void setKnowledgeBaseId(Long v){knowledgeBaseId=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;}
    public String getFileType(){return fileType;} public void setFileType(String v){fileType=v;}
    public String getMimeType(){return mimeType;} public void setMimeType(String v){mimeType=v;}
    public Long getFileSize(){return fileSize;} public void setFileSize(Long v){fileSize=v;}
    public String getStorageKey(){return storageKey;} public void setStorageKey(String v){storageKey=v;}
    public String getFileUrl(){return fileUrl;} public void setFileUrl(String v){fileUrl=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getProgress(){return progress;} public void setProgress(Integer v){progress=v;}
    public Integer getChunkCount(){return chunkCount;} public void setChunkCount(Integer v){chunkCount=v;}
    public String getCurrentTaskId(){return currentTaskId;} public void setCurrentTaskId(String v){currentTaskId=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
    public LocalDateTime getProcessedAt(){return processedAt;} public void setProcessedAt(LocalDateTime v){processedAt=v;}
}
