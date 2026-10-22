package com.studyagent.conversation.entity;

import java.time.LocalDateTime;

public class ConversationEntity {
    private Long id;
    private Long userId;
    private Long knowledgeBaseId;
    private String title;
    private String mode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long messageCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Long getMessageCount() { return messageCount; }
    public void setMessageCount(Long messageCount) { this.messageCount = messageCount; }
    private Long projectId;
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { this.projectId = value; }
    private Long knowledgeSnapshotId;
    public Long getKnowledgeSnapshotId() { return knowledgeSnapshotId; }
    public void setKnowledgeSnapshotId(Long value) { this.knowledgeSnapshotId = value; }
}
