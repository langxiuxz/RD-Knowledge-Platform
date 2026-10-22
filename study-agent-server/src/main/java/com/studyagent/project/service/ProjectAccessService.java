package com.studyagent.project.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.project.entity.ProjectEntity;
import com.studyagent.project.mapper.ProjectMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class ProjectAccessService {
    private final ProjectMapper projectMapper;

    public ProjectAccessService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public ProjectEntity requireMember(Long userId, Long projectId) {
        ProjectEntity project = projectMapper.findAccessible(projectId, userId);
        if (project != null) {
            return project;
        }
        if (projectMapper.findById(projectId) == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        throw new BusinessException(ErrorCode.PROJECT_NOT_MEMBER);
    }

    public ProjectEntity requireRole(Long userId, Long projectId, String... allowedRoles) {
        ProjectEntity project = requireMember(userId, projectId);
        if (Arrays.stream(allowedRoles).noneMatch(role -> role.equals(project.getMyRole()))) {
            throw new BusinessException(ErrorCode.PROJECT_ROLE_INSUFFICIENT);
        }
        return project;
    }
}
