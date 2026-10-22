package com.studyagent.onboarding.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class PathTaskEntity {
    private Long id;
    private Long pathId;
    private String type;
    private String title;
    private String description;
    private String resourceType;
    private Long resourceId;
    private String completionRule;
    private Integer estimatedMinutes;
    private Boolean required;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getPathId() { return pathId; }
    public void setPathId(Long value) { pathId = value; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String value) { resourceType = value; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long value) { resourceId = value; }
    public String getCompletionRule() { return completionRule; }
    public void setCompletionRule(String value) { completionRule = value; }
    public Integer getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(Integer value) { estimatedMinutes = value; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean value) { required = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
