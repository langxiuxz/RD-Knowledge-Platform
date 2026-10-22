package com.studyagent.knowledge.entity;

import java.time.LocalDateTime;

public class KnowledgeBaseEntity {
    private Long id;
    private Long projectId;
    private Long createdBy;
    private Long currentSnapshotId;
    private Integer currentSnapshotNo;
    private String name;
    private String description;
    private String status;
    private Long documentCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy=value; }
    public Long getCurrentSnapshotId() { return currentSnapshotId; }
    public void setCurrentSnapshotId(Long value) { currentSnapshotId=value; }
    public Integer getCurrentSnapshotNo() { return currentSnapshotNo; }
    public void setCurrentSnapshotNo(Integer value) { currentSnapshotNo=value; }
    public String getName() { return name; }
    public void setName(String value) { name=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public Long getDocumentCount() { return documentCount; }
    public void setDocumentCount(Long value) { documentCount=value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime=value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime=value; }
}
