package com.studyagent.project.entity;

import java.time.LocalDateTime;

public class ProjectMemberEntity {
    private Long id;
    private Long projectId;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private String status;
    private LocalDateTime joinedAt;
    private LocalDateTime removedAt;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
