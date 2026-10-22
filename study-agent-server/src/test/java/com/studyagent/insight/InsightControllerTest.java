package com.studyagent.insight;

import com.studyagent.assessment.controller.AssessmentReviewController;
import com.studyagent.assessment.service.AssessmentReviewService;
import com.studyagent.common.exception.*;
import com.studyagent.insight.controller.*;
import com.studyagent.insight.service.*;
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
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InsightControllerTest {
    @Mock CapabilityReportService reports;
    @Mock InsightService insights;
    @Mock AssessmentReviewService reviews;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new CapabilityReportController(reports),new InsightController(insights),new AssessmentReviewController(reviews))
            .setControllerAdvice(new GlobalExceptionHandler()).setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        var p=new UserPrincipal(7L,"manager");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void generationUsesJwtIdentityAndReturns202() throws Exception {
        when(reports.generate(7L,42L)).thenReturn(Map.of("reportId",9L,"status","PROCESSING"));
        mvc.perform(post("/api/assignments/42/capability-report/generate?userId=999"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.reportId").value(9));
        verify(reports).generate(7L,42L);
    }
    @ParameterizedTest @ValueSource(strings={"quiz-attempts","drill-runs","assignments"})
    void reviewUsesJwtIdentityNotReviewerId(String type) throws Exception {
        mvc.perform(post("/api/"+type+"/9/review").contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"PASS\",\"reviewerId\":999,\"userId\":999}"))
            .andExpect(status().isOk());
        switch(type) {
            case "quiz-attempts" -> verify(reviews).quiz(eq(7L),eq(9L),any());
            case "drill-runs" -> verify(reviews).drill(eq(7L),eq(9L),any());
            default -> verify(reviews).assignment(eq(7L),eq(9L),any());
        }
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"decision\":\"AUTO_PASS\"}","{\"decision\":\"PASS\",\"scoreOverride\":101}","{"})
    void invalidReviewsReturn400WithoutCallingService(String body) throws Exception {
        mvc.perform(post("/api/quiz-attempts/9/review").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(reviews);
    }
    @ParameterizedTest @ValueSource(strings={"/api/capability-reports/9","/api/projects/10/insights/overview","/api/projects/10/insights/assignments","/api/projects/10/insights/knowledge-gaps","/api/projects/10/insights/pending-reviews","/api/projects/10/insights/content-impact"})
    void unauthenticatedReadCannotReachServices(String url) throws Exception {
        SecurityContextHolder.clearContext(); mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports,insights,reviews);
    }
    @Test void unauthenticatedCommandsCannotReachServices() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/assignments/42/capability-report/generate")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/drill-runs/9/review").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"PASS\"}"))
            .andExpect(status().isUnauthorized()); verifyNoInteractions(reports,insights,reviews);
    }
    @Test void missingForbiddenAndInvalidFilterHaveContractStatuses() throws Exception {
        when(reports.get(7L,9L)).thenThrow(new BusinessException(ErrorCode.CAPABILITY_REPORT_NOT_FOUND));
        mvc.perform(get("/api/capability-reports/9")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(6001));
        when(insights.overview(7L,10L)).thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_INSUFFICIENT));
        mvc.perform(get("/api/projects/10/insights/overview")).andExpect(status().isForbidden());
        when(insights.contentImpact(7L,10L,null,null,"BAD")).thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED));
        mvc.perform(get("/api/projects/10/insights/content-impact?status=BAD")).andExpect(status().isBadRequest());
    }
}
