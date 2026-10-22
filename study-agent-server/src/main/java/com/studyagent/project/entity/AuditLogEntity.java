package com.studyagent.project.entity;

import java.time.LocalDateTime;

public class AuditLogEntity {
    private Long id;
    private Long projectId;
    private Long actorUserId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String detailJson;
    private String requestId;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
