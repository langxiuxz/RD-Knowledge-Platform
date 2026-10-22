package com.studyagent.onboarding;

import com.studyagent.common.exception.*;
import com.studyagent.onboarding.controller.OnboardingController;
import com.studyagent.onboarding.service.*;
import com.studyagent.onboarding.vo.OnboardingVOs;
import com.studyagent.security.UserPrincipal;
import org.junit.jupiter.api.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {
    @Mock OnboardingPathService paths;
    @Mock AssignmentService assignments;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new OnboardingController(paths,assignments))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        var p=new UserPrincipal(7L,"learner");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();}
    @Test void createUsesAuthenticatedIdentityNotClientIdentity() throws Exception {
        when(paths.create(eq(7L),eq(10L),any())).thenReturn(new OnboardingVOs.Id(8L));
        mvc.perform(post("/api/projects/10/onboarding-paths").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Orders\",\"knowledgeSnapshotId\":5,\"userId\":999,\"createdBy\":999}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(8));
        verify(paths).create(eq(7L),eq(10L),any());
    }
    @Test void executionUsesAuthenticatedIdentity() throws Exception {
        mvc.perform(post("/api/assignment-tasks/8/complete").contentType(MediaType.APPLICATION_JSON)
            .content("{\"note\":\"read\",\"userId\":999}"))
            .andExpect(status().isOk());
        verify(assignments).complete(eq(7L),eq(8L),argThat(r->"read".equals(r.note())));
    }
    @ParameterizedTest @ValueSource(strings={"/api/projects/10/onboarding-paths","/api/onboarding-paths/8","/api/projects/10/assignments","/api/assignments/8"})
    void noAuthenticationCannotReachServices(String url) throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(paths,assignments);
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"name\":\" \",\"knowledgeSnapshotId\":5}","{\"name\":\"x\",\"knowledgeSnapshotId\":0}","{"})
    void invalidPathReturns400(String body) throws Exception {
        mvc.perform(post("/api/projects/10/onboarding-paths").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());verifyNoInteractions(paths,assignments);
    }
    @ParameterizedTest @ValueSource(strings={"[null]","[0]","[-1]"})
    void invalidPrerequisiteElementsReturn400Not500(String ids) throws Exception {
        mvc.perform(post("/api/onboarding-paths/8/tasks").contentType(MediaType.APPLICATION_JSON)
            .content("{\"type\":\"CHECKPOINT\",\"title\":\"Review\",\"resourceType\":\"NONE\",\"completionRule\":\"MANUAL\",\"prerequisiteTaskIds\":"+ids+"}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(paths,assignments);
    }
    @ParameterizedTest @ValueSource(strings={"page=0","pageSize=101","status=DELETED","page=abc"})
    void invalidQueryReturns400(String query) throws Exception {
        mvc.perform(get("/api/projects/10/assignments?"+query)).andExpect(status().isBadRequest());
        verifyNoInteractions(paths,assignments);
    }
    @Test void missingAndForbiddenAreMapped() throws Exception {
        when(paths.get(7L,8L)).thenThrow(new BusinessException(ErrorCode.ONBOARDING_PATH_NOT_FOUND));
        mvc.perform(get("/api/onboarding-paths/8")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(5001));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(assignments).start(7L,9L);
        mvc.perform(post("/api/assignments/9/start")).andExpect(status().isForbidden());
    }
}
