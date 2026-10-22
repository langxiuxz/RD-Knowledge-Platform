package com.studyagent.project;

import com.studyagent.auth.entity.UserEntity;
import com.studyagent.auth.mapper.UserMapper;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.project.dto.ProjectQueries;
import com.studyagent.project.dto.ProjectRequests;
import com.studyagent.project.entity.ProjectEntity;
import com.studyagent.project.entity.ProjectMemberEntity;
import com.studyagent.project.mapper.AuditLogMapper;
import com.studyagent.project.mapper.ProjectMapper;
import com.studyagent.project.service.ProjectAccessService;
import com.studyagent.project.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock ProjectMapper projects;
    @Mock AuditLogMapper audit;
    @Mock UserMapper users;
    ProjectAccessService access;
    ProjectService service;

    @BeforeEach
    void setUp() {
        access = new ProjectAccessService(projects);
        service = new ProjectService(projects, audit, users, access, new ObjectMapper());
    }

    @Test
    void createRegistersCreatorAsOwnerAndWritesAudit() {
        when(projects.insert(any())).thenAnswer(call -> {
            ProjectEntity project = call.getArgument(0);
            project.setId(10L);
            assertThat(project.getCreatedBy()).isEqualTo(7L);
            return 1;
        });
        assertThat(service.create(7L, new ProjectRequests.Create("Orders", "orders", null)).id()).isEqualTo(10L);
        verify(projects).insertMember(10L, 7L, "OWNER");
        verify(audit).insert(argThat(log -> log.getProjectId().equals(10L)
                && log.getActorUserId().equals(7L) && log.getAction().equals("PROJECT_CREATED")));
    }

    @Test
    void duplicateProjectCodePrecheckDoesNotWrite() {
        when(projects.codeExists("orders", null)).thenReturn(true);
        failsWith(ErrorCode.PROJECT_CODE_EXISTS, () -> service.create(7L,
                new ProjectRequests.Create("Orders", "orders", null)));
        verify(projects, never()).insert(any());
        verifyNoInteractions(audit);
    }

    @Test
    void concurrentDuplicateProjectCodeMapsDatabaseConflict() {
        when(projects.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        failsWith(ErrorCode.PROJECT_CODE_EXISTS, () -> service.create(7L,
                new ProjectRequests.Create("Orders", "orders", null)));
        verify(projects, never()).insertMember(anyLong(), anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void nonexistentProjectIsDistinctFromNonmemberOrRemovedMember() {
        failsWith(ErrorCode.PROJECT_NOT_FOUND, () -> access.requireMember(7L, 10L));
        when(projects.findById(10L)).thenReturn(project("OWNER", "ACTIVE"));
        failsWith(ErrorCode.PROJECT_NOT_MEMBER, () -> access.requireMember(7L, 10L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "MAINTAINER", "LEARNER"})
    void activeMemberCanReadProjectAndMembers(String role) {
        when(projects.findAccessible(10L, 7L)).thenReturn(project(role, "ACTIVE"));
        assertThat(service.get(7L, 10L).myRole()).isEqualTo(role);
        service.members(7L, 10L, new ProjectQueries.Members(null, null, null, null));
        verify(projects).findMembers(eq(10L), any(), eq(0L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAINTAINER", "LEARNER"})
    void nonOwnerCannotMutateAnyProjectSettingOrMember(String role) {
        when(projects.lockById(10L)).thenReturn(project(role, "ACTIVE"));
        when(projects.findAccessible(10L, 7L)).thenReturn(project(role, "ACTIVE"));
        failsWith(ErrorCode.PROJECT_ROLE_INSUFFICIENT, () -> service.update(7L, 10L,
                new ProjectRequests.Update("Orders", "orders", null)));
        failsWith(ErrorCode.PROJECT_ROLE_INSUFFICIENT, () -> service.archive(7L, 10L));
        failsWith(ErrorCode.PROJECT_ROLE_INSUFFICIENT, () -> service.addMember(7L, 10L,
                new ProjectRequests.AddMember("lin", "LEARNER")));
        failsWith(ErrorCode.PROJECT_ROLE_INSUFFICIENT, () -> service.changeRole(7L, 10L, 8L,
                new ProjectRequests.ChangeRole("MAINTAINER")));
        failsWith(ErrorCode.PROJECT_ROLE_INSUFFICIENT, () -> service.removeMember(7L, 10L, 8L));
        verify(projects, never()).lockMember(anyLong(), anyLong());
        verify(projects, never()).update(any());
        verifyNoInteractions(audit, users);
    }

    @Test
    void lastOwnerCannotBeRemovedOrDemoted() {
        owner();
        when(projects.lockMember(10L, 7L)).thenReturn(member(7L, "OWNER", "ACTIVE"));
        when(projects.countActiveOwners(10L)).thenReturn(1L);
        failsWith(ErrorCode.PROJECT_LAST_OWNER, () -> service.removeMember(7L, 10L, 7L));
        failsWith(ErrorCode.PROJECT_LAST_OWNER, () -> service.changeRole(7L, 10L, 7L,
                new ProjectRequests.ChangeRole("MAINTAINER")));
        verify(projects, never()).removeMember(anyLong(), anyLong());
        verify(projects, never()).updateMemberRole(anyLong(), anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void oneOfSeveralOwnersCanBeDemotedAndAuditCapturesOldAndNewRole() {
        owner();
        when(projects.lockMember(10L, 8L)).thenReturn(member(8L, "OWNER", "ACTIVE"));
        when(projects.countActiveOwners(10L)).thenReturn(2L);
        service.changeRole(7L, 10L, 8L, new ProjectRequests.ChangeRole("LEARNER"));
        var order = inOrder(projects);
        order.verify(projects).lockById(10L);
        order.verify(projects).findAccessible(10L, 7L);
        order.verify(projects).lockMember(10L, 8L);
        order.verify(projects).countActiveOwners(10L);
        order.verify(projects).updateMemberRole(10L, 8L, "LEARNER");
        verify(audit).insert(argThat(log -> log.getAction().equals("PROJECT_MEMBER_ROLE_CHANGED")
                && log.getDetailJson().contains("oldRole") && log.getDetailJson().contains("newRole")));
    }

    @Test
    void unchangedRoleAndRepeatedRemovalDoNotWriteDuplicateAudit() {
        owner();
        when(projects.lockMember(10L, 8L)).thenReturn(member(8L, "LEARNER", "ACTIVE"));
        service.changeRole(7L, 10L, 8L, new ProjectRequests.ChangeRole("LEARNER"));
        when(projects.lockMember(10L, 8L)).thenReturn(member(8L, "LEARNER", "REMOVED"));
        service.removeMember(7L, 10L, 8L);
        verifyNoInteractions(audit);
        verify(projects, never()).updateMemberRole(anyLong(), anyLong(), anyString());
        verify(projects, never()).removeMember(anyLong(), anyLong());
    }

    @Test
    void addingActiveDuplicateMemberIsConflict() {
        owner();
        registeredUser();
        when(projects.lockMember(10L, 8L)).thenReturn(member(8L, "LEARNER", "ACTIVE"));
        failsWith(ErrorCode.PROJECT_MEMBER_EXISTS, () -> service.addMember(7L, 10L,
                new ProjectRequests.AddMember("lin", "LEARNER")));
        verifyNoInteractions(audit);
    }

    @Test
    void removedMemberRejoinsUsingExistingMembership() {
        owner();
        registeredUser();
        when(projects.lockMember(10L, 8L)).thenReturn(member(8L, "LEARNER", "REMOVED"));
        service.addMember(7L, 10L, new ProjectRequests.AddMember("lin", "MAINTAINER"));
        verify(projects).reactivateMember(10L, 8L, "MAINTAINER");
        verify(projects, never()).insertMember(anyLong(), anyLong(), anyString());
        verify(audit).insert(argThat(log -> log.getAction().equals("PROJECT_MEMBER_ADDED")));
    }

    @Test
    void unknownOrDisabledUserCannotBeAdded() {
        owner();
        failsWith(ErrorCode.PROJECT_MEMBER_USER_NOT_FOUND, () -> service.addMember(7L, 10L,
                new ProjectRequests.AddMember("lin", "LEARNER")));
        verify(projects, never()).lockMember(anyLong(), anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void archivedProjectIsReadableButCannotBeChangedAndRepeatedArchiveIsIdempotent() {
        when(projects.lockById(10L)).thenReturn(project("OWNER", "ARCHIVED"));
        when(projects.findAccessible(10L, 7L)).thenReturn(project("OWNER", "ARCHIVED"));
        assertThat(service.get(7L, 10L).status()).isEqualTo("ARCHIVED");
        service.archive(7L, 10L);
        failsWith(ErrorCode.PROJECT_ARCHIVED, () -> service.update(7L, 10L,
                new ProjectRequests.Update("Orders", "orders", null)));
        failsWith(ErrorCode.PROJECT_ARCHIVED, () -> service.addMember(7L, 10L,
                new ProjectRequests.AddMember("lin", "LEARNER")));
        failsWith(ErrorCode.PROJECT_ARCHIVED, () -> service.changeRole(7L, 10L, 8L,
                new ProjectRequests.ChangeRole("OWNER")));
        failsWith(ErrorCode.PROJECT_ARCHIVED, () -> service.removeMember(7L, 10L, 8L));
        verify(projects, never()).archive(anyLong());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "MAINTAINER"})
    void ownerAndMaintainerCanQueryAudit(String role) {
        when(projects.findAccessible(10L, 7L)).thenReturn(project(role, "ACTIVE"));
        service.auditLogs(7L, 10L, new ProjectQueries.Audit(null, null, 8L, null, null, 2, 5));
        verify(audit).findPage(eq(10L), any(), eq(5L));
    }

    @Test
    void learnerCannotReadAudit() {
        when(projects.findAccessible(10L, 7L)).thenReturn(project("LEARNER", "ACTIVE"));
        failsWith(ErrorCode.PROJECT_ROLE_INSUFFICIENT, () -> service.auditLogs(7L, 10L,
                new ProjectQueries.Audit(null, null, null, null, null, null, null)));
        verifyNoInteractions(audit);
    }

    private void owner() {
        when(projects.lockById(10L)).thenReturn(project("OWNER", "ACTIVE"));
        when(projects.findAccessible(10L, 7L)).thenReturn(project("OWNER", "ACTIVE"));
    }

    private void registeredUser() {
        var user = new UserEntity();
        user.setId(8L);
        when(users.findActiveByUsername("lin")).thenReturn(user);
    }

    private static ProjectEntity project(String role, String status) {
        var project = new ProjectEntity();
        project.setId(10L);
        project.setCode("orders");
        project.setMyRole(role);
        project.setStatus(status);
        return project;
    }

    private static ProjectMemberEntity member(Long userId, String role, String status) {
        var member = new ProjectMemberEntity();
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(status);
        return member;
    }

    private static void failsWith(ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(code));
    }
}
