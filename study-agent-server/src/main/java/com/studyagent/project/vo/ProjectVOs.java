package com.studyagent.project.vo;

import com.studyagent.project.entity.ProjectEntity;

import java.time.LocalDateTime;

public final class ProjectVOs {
    private ProjectVOs() {}

    public record Summary(Long id, String name, String code, String role, String status,
                          Long memberCount, LocalDateTime updatedAt) {
        public static Summary from(ProjectEntity project) {
            return new Summary(project.getId(), project.getName(), project.getCode(), project.getMyRole(),
                    project.getStatus(), project.getMemberCount(), project.getUpdateTime());
        }
    }

    public record Project(Long id, String name, String code, String description, String status,
                          String myRole, Long memberCount, LocalDateTime createTime, LocalDateTime updateTime) {
        public static Project from(ProjectEntity project) {
            return new Project(project.getId(), project.getName(), project.getCode(), project.getDescription(),
                    project.getStatus(), project.getMyRole(), project.getMemberCount(),
                    project.getCreateTime(), project.getUpdateTime());
        }
    }

    public record Id(Long id) {}

    public record Member(Long userId, String username, String nickname, String role, String status,
                         LocalDateTime joinedAt) {}

    public record Audit(Long id, Long userId, String action, String resourceType, Long resourceId,
                        Object detail, LocalDateTime createTime) {}
}
