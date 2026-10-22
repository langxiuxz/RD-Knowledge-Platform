package com.studyagent.onboarding.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class AssignmentTaskEntity {
    private Long id;
    private Long assignmentId;
    private Long sourcePathTaskId;
    private String type;
    private String title;
    private String resourceType;
    private Long resourceId;
    private String prerequisiteTaskIdsJson;
    private String completionRule;
    private Boolean required;
    private Integer sortOrder;
    private String status;
    private Long quizAttemptId;
    private Long drillRunId;
    private String completionNote;
    private LocalDateTime openedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long value) { assignmentId = value; }
    public Long getSourcePathTaskId() { return sourcePathTaskId; }
    public void setSourcePathTaskId(Long value) { sourcePathTaskId = value; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String value) { resourceType = value; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long value) { resourceId = value; }
    public String getPrerequisiteTaskIdsJson() { return prerequisiteTaskIdsJson; }
    public void setPrerequisiteTaskIdsJson(String value) { prerequisiteTaskIdsJson = value; }
    public String getCompletionRule() { return completionRule; }
    public void setCompletionRule(String value) { completionRule = value; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean value) { required = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getQuizAttemptId() { return quizAttemptId; }
    public void setQuizAttemptId(Long value) { quizAttemptId = value; }
    public Long getDrillRunId() { return drillRunId; }
    public void setDrillRunId(Long value) { drillRunId = value; }
    public String getCompletionNote() { return completionNote; }
    public void setCompletionNote(String value) { completionNote = value; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime value) { openedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
