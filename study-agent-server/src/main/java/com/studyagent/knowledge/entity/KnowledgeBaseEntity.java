package com.studyagent.knowledge.entity;

import java.time.LocalDateTime;

public class KnowledgeBaseEntity {
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String embeddingModel;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long documentCount;
    private Long chunkCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Long getDocumentCount() { return documentCount; }
    public void setDocumentCount(Long documentCount) { this.documentCount = documentCount; }
    public Long getChunkCount() { return chunkCount; }
    public void setChunkCount(Long chunkCount) { this.chunkCount = chunkCount; }
}
