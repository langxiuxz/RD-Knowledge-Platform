package com.studyagent.project.entity;

import java.time.LocalDateTime;

public class ProjectEntity {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String status;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime archivedAt;
    private String myRole;
    private Long memberCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
    public String getMyRole() { return myRole; }
    public void setMyRole(String myRole) { this.myRole = myRole; }
    public Long getMemberCount() { return memberCount; }
    public void setMemberCount(Long memberCount) { this.memberCount = memberCount; }
}
