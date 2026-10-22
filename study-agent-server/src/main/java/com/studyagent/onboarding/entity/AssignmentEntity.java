package com.studyagent.onboarding.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class AssignmentEntity {
    private Long id;
    private Long projectId;
    private Long pathId;
    private Integer pathVersion;
    private Long knowledgeSnapshotId;
    private Long assigneeUserId;
    private Long assignedBy;
    private String status;
    private BigDecimal progress;
    private LocalDateTime dueAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Long getPathId() { return pathId; }
    public void setPathId(Long value) { pathId = value; }
    public Integer getPathVersion() { return pathVersion; }
    public void setPathVersion(Integer value) { pathVersion = value; }
    public Long getKnowledgeSnapshotId() { return knowledgeSnapshotId; }
    public void setKnowledgeSnapshotId(Long value) { knowledgeSnapshotId = value; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(Long value) { assigneeUserId = value; }
    public Long getAssignedBy() { return assignedBy; }
    public void setAssignedBy(Long value) { assignedBy = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public BigDecimal getProgress() { return progress; }
    public void setProgress(BigDecimal value) { progress = value; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime value) { dueAt = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
