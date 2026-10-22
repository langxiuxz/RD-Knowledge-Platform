package com.studyagent.onboarding.service;

import com.studyagent.common.exception.*;
import com.studyagent.project.entity.*;
import com.studyagent.project.mapper.*;
import com.studyagent.project.service.ProjectAccessService;
import org.springframework.stereotype.Component;

@Component
public class OnboardingSupport {
    private final ProjectMapper projects;
    private final ProjectAccessService access;
    private final AuditLogMapper audit;
    public OnboardingSupport(ProjectMapper projects,ProjectAccessService access,AuditLogMapper audit) {
        this.projects=projects; this.access=access; this.audit=audit;
    }
    public ProjectEntity member(Long userId,Long projectId) { return access.requireMember(userId,projectId); }
    /** Shared project lock serializes against member removal, role change and project archival. */
    public void lockWrite(Long userId,Long projectId,boolean manager) {
        ProjectEntity project=projects.lockById(projectId);
        if(project==null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        ProjectMemberEntity member=projects.lockMember(projectId,userId);
        if(member==null || !"ACTIVE".equals(member.getStatus())) throw new BusinessException(ErrorCode.PROJECT_NOT_MEMBER);
        if(manager && !"OWNER".equals(member.getRole()) && !"MAINTAINER".equals(member.getRole()))
            throw new BusinessException(ErrorCode.PROJECT_ROLE_INSUFFICIENT);
        if(!"ACTIVE".equals(project.getStatus())) throw new BusinessException(ErrorCode.PROJECT_ARCHIVED);
    }
    public void requireLearner(Long projectId,Long userId) {
        ProjectMemberEntity member=projects.lockMember(projectId,userId);
        if(member==null || !"ACTIVE".equals(member.getStatus()) || !"LEARNER".equals(member.getRole()))
            throw new BusinessException(ErrorCode.ONBOARDING_PATH_INVALID,"分配对象必须是当前项目的 ACTIVE LEARNER");
    }
    public void audit(Long projectId,Long userId,String action,String type,Long id) {
        AuditLogEntity event=new AuditLogEntity();
        event.setProjectId(projectId); event.setActorUserId(userId); event.setAction(action);
        event.setResourceType(type); event.setResourceId(id); event.setDetailJson("{}");
        if(audit.insert(event)!=1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
