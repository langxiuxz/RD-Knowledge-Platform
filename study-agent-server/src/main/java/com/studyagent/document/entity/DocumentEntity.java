package com.studyagent.document.entity;

import java.time.LocalDateTime;

public class DocumentEntity {
    private Long id;
    private Long projectId;
    private Long knowledgeBaseId;
    private Long createdBy;
    private String title;
    private String status;
    private Long currentPublishedVersionId;
    private Integer currentVersionNo;
    private String latestVersionProcessStatus;
    private String latestVersionPublishStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long value) { knowledgeBaseId=value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy=value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public Long getCurrentPublishedVersionId() { return currentPublishedVersionId; }
    public void setCurrentPublishedVersionId(Long value) { currentPublishedVersionId=value; }
    public Integer getCurrentVersionNo() { return currentVersionNo; }
    public void setCurrentVersionNo(Integer value) { currentVersionNo=value; }
    public String getLatestVersionProcessStatus() { return latestVersionProcessStatus; }
    public void setLatestVersionProcessStatus(String value) { latestVersionProcessStatus=value; }
    public String getLatestVersionPublishStatus() { return latestVersionPublishStatus; }
    public void setLatestVersionPublishStatus(String value) { latestVersionPublishStatus=value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime=value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime=value; }
}
