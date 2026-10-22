package com.studyagent.assessment;

import com.studyagent.ai.client.AiClient;
import com.studyagent.assessment.dto.AssessmentRequests;
import com.studyagent.assessment.entity.DrillRuleEntity;
import com.studyagent.assessment.entity.DrillRunActionEntity;
import com.studyagent.assessment.entity.DrillRunEntity;
import com.studyagent.assessment.entity.DrillScenarioEntity;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.assessment.service.AssessmentSupport;
import com.studyagent.assessment.service.AssessmentViews;
import com.studyagent.assessment.service.DrillActionPersistence;
import com.studyagent.assessment.service.DrillRunService;
import com.studyagent.onboarding.service.AssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrillRunServiceTest {
    @Mock AssessmentMapper mapper;
    @Mock AssessmentSupport support;
    @Mock AssessmentViews views;
    @Mock DrillActionPersistence actionPersistence;
    @Mock AiClient ai;
    @Mock AssignmentService assignments;

    @Test
    void submittedRunKeepsProgramScorePendingHumanReviewWithoutCompletingAssignmentTask() {
        var service = new DrillRunService(mapper, support, views, actionPersistence, ai, assignments);
        var run = new DrillRunEntity();
        run.setId(70L);
        run.setProjectId(11L);
        run.setScenarioId(31L);
        run.setUserId(2L);
        run.setAssignmentTaskId(90L);
        run.setStatus("IN_PROGRESS");
        when(mapper.run(70L)).thenReturn(run);
        when(mapper.lockRun(70L)).thenReturn(run);
        when(support.write(any())).thenReturn("conclusion-json");
        when(mapper.unlockedEvidenceCodes(70L)).thenReturn(List.of());

        var rule = new DrillRuleEntity();
        rule.setId(1L);
        rule.setCode("VERIFY");
        rule.setRuleType("VALIDATION");
        rule.setPoints(new BigDecimal("100"));
        when(mapper.rules(31L)).thenReturn(List.of(rule));
        var completedValidation = new DrillRunActionEntity();
        completedValidation.setMatchedRuleId(1L);
        when(mapper.actions(70L)).thenReturn(List.of(completedValidation));
        var scenario = new DrillScenarioEntity();
        scenario.setPassScore(new BigDecimal("70"));
        when(mapper.scenario(31L)).thenReturn(scenario);
        when(mapper.evidence(31L)).thenReturn(List.of());
        when(mapper.completeRun(run)).thenReturn(1);

        var request = new AssessmentRequests.Conclusion("cause", "recovery", "validate", List.of());
        var result = service.submit(2L, 70L, request);

        assertEquals("REVIEW_PENDING", result.status());
        assertTrue(result.reviewRequired());
        assertEquals(Boolean.TRUE, result.passCandidate());
        assertEquals(new BigDecimal("100.00"), result.programScore());
        assertEquals("REVIEW_PENDING", run.getStatus());
        assertNull(run.getFinalScore());
        verify(assignments, never()).completeFromAssessment(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }
}
