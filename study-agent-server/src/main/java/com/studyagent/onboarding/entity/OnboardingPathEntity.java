package com.studyagent.onboarding.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class OnboardingPathEntity {
    private Long id;
    private Long projectId;
    private String name;
    private String targetRole;
    private String description;
    private String status;
    private Integer versionNo;
    private Long knowledgeSnapshotId;
    private Long createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String value) { targetRole = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer value) { versionNo = value; }
    public Long getKnowledgeSnapshotId() { return knowledgeSnapshotId; }
    public void setKnowledgeSnapshotId(Long value) { knowledgeSnapshotId = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
