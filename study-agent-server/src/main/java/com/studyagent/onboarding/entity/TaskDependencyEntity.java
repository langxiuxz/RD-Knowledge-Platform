package com.studyagent.onboarding.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class TaskDependencyEntity {
    private Long id;
    private Long pathId;
    private Long taskId;
    private Long prerequisiteTaskId;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getPathId() { return pathId; }
    public void setPathId(Long value) { pathId = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getPrerequisiteTaskId() { return prerequisiteTaskId; }
    public void setPrerequisiteTaskId(Long value) { prerequisiteTaskId = value; }
}
