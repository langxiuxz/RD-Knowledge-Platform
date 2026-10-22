package com.studyagent.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.auth.mapper.UserMapper;
import com.studyagent.common.exception.GlobalExceptionHandler;
import com.studyagent.project.controller.ProjectController;
import com.studyagent.project.entity.AuditLogEntity;
import com.studyagent.project.entity.ProjectEntity;
import com.studyagent.project.mapper.AuditLogMapper;
import com.studyagent.project.mapper.ProjectMapper;
import com.studyagent.project.service.ProjectAccessService;
import com.studyagent.project.service.ProjectService;
import com.studyagent.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {
    @Mock ProjectMapper projects;
    @Mock AuditLogMapper audit;
    @Mock UserMapper users;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new ProjectService(projects, audit, users, new ProjectAccessService(projects), new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new ProjectController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        var principal = new UserPrincipal(7L, "owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void creationUsesJwtIdentityAndIgnoresClientIdentityAndRole() throws Exception {
        when(projects.insert(any())).thenAnswer(call -> {
            ProjectEntity project = call.getArgument(0);
            project.setId(10L);
            return 1;
        });
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Orders","code":"orders","userId":999,"createdBy":999,"role":"LEARNER"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(10));
        verify(projects).insert(argThat(project -> project.getCreatedBy().equals(7L)));
        verify(projects).insertMember(10L, 7L, "OWNER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/projects", "/api/projects/10", "/api/projects/10/members", "/api/projects/10/audit-logs"})
    void unauthenticatedReadDoesNotReachMappers(String url) throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(projects, audit, users);
    }

    @Test
    void unauthenticatedCreationDoesNotWrite() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Orders\",\"code\":\"orders\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(projects, audit, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \",\"code\":\"orders\"}",
            "{\"name\":\"Orders\",\"code\":\"bad code\"}", "{"})
    void invalidCreationIs400(String body) throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(projects, audit, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "pageSize=101", "page=abc", "role=ADMIN", "status=DELETED"})
    void invalidProjectQueryIs400(String query) throws Exception {
        mvc.perform(get("/api/projects?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(projects, audit, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/projects/0", "/api/projects/-1", "/api/projects/abc", "/api/projects/10/members/0"})
    void invalidPathIs400(String url) throws Exception {
        mvc.perform(url.contains("members") ? delete(url) : get(url)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(projects, audit, users);
    }

    @Test
    void nonmemberIsForbiddenEvenWhenClaimingOwnerIdInQuery() throws Exception {
        when(projects.findById(10L)).thenReturn(project("OWNER"));
        mvc.perform(get("/api/projects/10?userId=1"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1102));
        verify(projects).findAccessible(10L, 7L);
    }

    @Test
    void missingProjectIs404() throws Exception {
        mvc.perform(get("/api/projects/10")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1101));
    }

    @Test
    void summaryMatchesContractAndHasNoPersistenceFields() throws Exception {
        when(projects.countProjects(eq(7L), any())).thenReturn(1L);
        when(projects.findProjects(eq(7L), any(), eq(0L))).thenReturn(List.of(project("LEARNER")));
        mvc.perform(get("/api/projects?userId=999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].role").value("LEARNER"))
                .andExpect(jsonPath("$.data.rows[0].updatedAt").isString())
                .andExpect(jsonPath("$.data.rows[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].myRole").doesNotExist());
    }

    @Test
    void auditDetailSerializesAsJsonObjectAndUserIdIsOnlyAFilter() throws Exception {
        when(projects.findAccessible(10L, 7L)).thenReturn(project("MAINTAINER"));
        var log = new AuditLogEntity();
        log.setId(51L);
        log.setActorUserId(8L);
        log.setDetailJson("{\"oldRole\":\"LEARNER\",\"newRole\":\"MAINTAINER\"}");
        when(audit.findPage(eq(10L), any(), eq(0L))).thenReturn(List.of(log));
        mvc.perform(get("/api/projects/10/audit-logs?userId=8&startTime=2026-10-20T10:00:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].userId").value(8))
                .andExpect(jsonPath("$.data.rows[0].detail.newRole").value("MAINTAINER"));
        verify(audit).count(eq(10L), argThat(query -> query.userId().equals(8L)
                && query.startTime().equals(LocalDateTime.of(2026, 10, 20, 10, 0))));
    }

    @Test
    void invalidAuditDateAndReversedRangeAre400() throws Exception {
        mvc.perform(get("/api/projects/10/audit-logs?startTime=invalid"))
                .andExpect(status().isBadRequest());
        when(projects.findAccessible(10L, 7L)).thenReturn(project("OWNER"));
        mvc.perform(get("/api/projects/10/audit-logs?startTime=2026-10-21T00:00:00&endTime=2026-10-20T00:00:00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(audit);
    }

    private static ProjectEntity project(String role) {
        var project = new ProjectEntity();
        project.setId(10L);
        project.setName("Orders");
        project.setCode("orders");
        project.setMyRole(role);
        project.setStatus("ACTIVE");
        project.setMemberCount(3L);
        project.setUpdateTime(LocalDateTime.of(2026, 10, 20, 10, 0));
        return project;
    }
}
