package com.studyagent.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.auth.entity.UserEntity;
import com.studyagent.auth.mapper.UserMapper;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.project.dto.ProjectQueries;
import com.studyagent.project.dto.ProjectRequests;
import com.studyagent.project.entity.AuditLogEntity;
import com.studyagent.project.entity.ProjectEntity;
import com.studyagent.project.entity.ProjectMemberEntity;
import com.studyagent.project.mapper.AuditLogMapper;
import com.studyagent.project.mapper.ProjectMapper;
import com.studyagent.project.vo.ProjectVOs;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ProjectService {
    private final ProjectMapper projectMapper;
    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;
    private final ProjectAccessService accessService;
    private final ObjectMapper objectMapper;

    public ProjectService(ProjectMapper projectMapper, AuditLogMapper auditLogMapper, UserMapper userMapper,
                          ProjectAccessService accessService, ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.auditLogMapper = auditLogMapper;
        this.userMapper = userMapper;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    public PageResult<ProjectVOs.Summary> list(Long userId, ProjectQueries.Projects query) {
        long total = projectMapper.countProjects(userId, query);
        var rows = projectMapper.findProjects(userId, query, offset(query.page(), query.pageSize())).stream()
                .map(ProjectVOs.Summary::from).toList();
        return new PageResult<>(total, rows);
    }

    @Transactional
    public ProjectVOs.Id create(Long userId, ProjectRequests.Create request) {
        if (projectMapper.codeExists(request.code(), null)) {
            throw new BusinessException(ErrorCode.PROJECT_CODE_EXISTS);
        }
        ProjectEntity project = new ProjectEntity();
        project.setName(request.name());
        project.setCode(request.code());
        project.setDescription(request.description());
        project.setCreatedBy(userId);
        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PROJECT_CODE_EXISTS);
        }
        projectMapper.insertMember(project.getId(), userId, "OWNER");
        writeAudit(project.getId(), userId, "PROJECT_CREATED", "PROJECT", project.getId(),
                Map.of("code", request.code()));
        return new ProjectVOs.Id(project.getId());
    }

    public ProjectVOs.Project get(Long userId, Long projectId) {
        return ProjectVOs.Project.from(accessService.requireMember(userId, projectId));
    }

    @Transactional
    public void update(Long userId, Long projectId, ProjectRequests.Update request) {
        ProjectEntity project = lockActiveProjectAsOwner(userId, projectId);
        if (projectMapper.codeExists(request.code(), projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_CODE_EXISTS);
        }
        String oldCode = project.getCode();
        project.setName(request.name());
        project.setCode(request.code());
        project.setDescription(request.description());
        try {
            projectMapper.update(project);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PROJECT_CODE_EXISTS);
        }
        writeAudit(projectId, userId, "PROJECT_UPDATED", "PROJECT", projectId,
                Map.of("oldCode", oldCode, "code", request.code(), "name", request.name()));
    }

    @Transactional
    public void archive(Long userId, Long projectId) {
        ProjectEntity project = lockProject(projectId);
        accessService.requireRole(userId, projectId, "OWNER");
        if ("ARCHIVED".equals(project.getStatus())) {
            return;
        }
        projectMapper.archive(projectId);
        writeAudit(projectId, userId, "PROJECT_ARCHIVED", "PROJECT", projectId, Map.of("status", "ARCHIVED"));
    }

    public PageResult<ProjectVOs.Member> members(Long userId, Long projectId, ProjectQueries.Members query) {
        accessService.requireMember(userId, projectId);
        long total = projectMapper.countMembers(projectId, query);
        var rows = projectMapper.findMembers(projectId, query, offset(query.page(), query.pageSize())).stream()
                .map(member -> new ProjectVOs.Member(member.getUserId(), member.getUsername(), member.getNickname(),
                        member.getRole(), member.getStatus(), member.getJoinedAt())).toList();
        return new PageResult<>(total, rows);
    }

    @Transactional
    public void addMember(Long actorId, Long projectId, ProjectRequests.AddMember request) {
        lockActiveProjectAsOwner(actorId, projectId);
        UserEntity user = userMapper.findActiveByUsername(request.username());
        if (user == null) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_USER_NOT_FOUND);
        }
        ProjectMemberEntity existing = projectMapper.lockMember(projectId, user.getId());
        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_EXISTS);
        }
        if (existing == null) {
            projectMapper.insertMember(projectId, user.getId(), request.role());
        } else {
            projectMapper.reactivateMember(projectId, user.getId(), request.role());
        }
        writeAudit(projectId, actorId, "PROJECT_MEMBER_ADDED", "PROJECT_MEMBER", user.getId(),
                Map.of("userId", user.getId(), "role", request.role()));
    }

    @Transactional
    public void changeRole(Long actorId, Long projectId, Long targetUserId, ProjectRequests.ChangeRole request) {
        lockActiveProjectAsOwner(actorId, projectId);
        ProjectMemberEntity target = requireActiveMember(projectId, targetUserId);
        if (target.getRole().equals(request.role())) {
            return;
        }
        protectLastOwner(projectId, target);
        projectMapper.updateMemberRole(projectId, targetUserId, request.role());
        writeAudit(projectId, actorId, "PROJECT_MEMBER_ROLE_CHANGED", "PROJECT_MEMBER", targetUserId,
                Map.of("userId", targetUserId, "oldRole", target.getRole(), "newRole", request.role()));
    }

    @Transactional
    public void removeMember(Long actorId, Long projectId, Long targetUserId) {
        lockActiveProjectAsOwner(actorId, projectId);
        ProjectMemberEntity target = projectMapper.lockMember(projectId, targetUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND);
        }
        if ("REMOVED".equals(target.getStatus())) {
            return;
        }
        protectLastOwner(projectId, target);
        projectMapper.removeMember(projectId, targetUserId);
        writeAudit(projectId, actorId, "PROJECT_MEMBER_REMOVED", "PROJECT_MEMBER", targetUserId,
                Map.of("userId", targetUserId, "previousRole", target.getRole()));
    }

    public PageResult<ProjectVOs.Audit> auditLogs(Long userId, Long projectId, ProjectQueries.Audit query) {
        accessService.requireRole(userId, projectId, "OWNER", "MAINTAINER");
        if (query.startTime() != null && query.endTime() != null && query.startTime().isAfter(query.endTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "startTime不能晚于endTime");
        }
        long total = auditLogMapper.count(projectId, query);
        var rows = auditLogMapper.findPage(projectId, query, offset(query.page(), query.pageSize())).stream()
                .map(this::toAuditVO).toList();
        return new PageResult<>(total, rows);
    }

    private ProjectEntity lockActiveProjectAsOwner(Long userId, Long projectId) {
        ProjectEntity project = lockProject(projectId);
        accessService.requireRole(userId, projectId, "OWNER");
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.PROJECT_ARCHIVED);
        }
        return project;
    }

    private ProjectEntity lockProject(Long projectId) {
        // Serialize all membership/settings writes before reading any membership snapshot.
        // This also protects the last OWNER from concurrent demotions/removals under MySQL REPEATABLE READ.
        ProjectEntity project = projectMapper.lockById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private ProjectMemberEntity requireActiveMember(Long projectId, Long userId) {
        ProjectMemberEntity member = projectMapper.lockMember(projectId, userId);
        if (member == null || !"ACTIVE".equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private void protectLastOwner(Long projectId, ProjectMemberEntity target) {
        if ("OWNER".equals(target.getRole()) && projectMapper.countActiveOwners(projectId) <= 1) {
            throw new BusinessException(ErrorCode.PROJECT_LAST_OWNER);
        }
    }

    private void writeAudit(Long projectId, Long actorId, String action, String resourceType,
                            Long resourceId, Object detail) {
        AuditLogEntity log = new AuditLogEntity();
        log.setProjectId(projectId);
        log.setActorUserId(actorId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        try {
            log.setDetailJson(objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审计详情序列化失败");
        }
        auditLogMapper.insert(log);
    }

    private ProjectVOs.Audit toAuditVO(AuditLogEntity log) {
        try {
            // Return plain JSON values so the existing Jackson 3 HTTP converter can serialize them.
            Object detail = log.getDetailJson() == null ? null : objectMapper.readValue(log.getDetailJson(), Object.class);
            return new ProjectVOs.Audit(log.getId(), log.getActorUserId(), log.getAction(), log.getResourceType(),
                    log.getResourceId(), detail, log.getCreateTime());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审计详情读取失败");
        }
    }

    private long offset(Integer page, Integer pageSize) {
        return (page.longValue() - 1L) * pageSize;
    }
}
