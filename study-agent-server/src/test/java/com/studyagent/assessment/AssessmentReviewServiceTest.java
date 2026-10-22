package com.studyagent.assessment;

import com.studyagent.assessment.dto.AssessmentRequests.Review;
import com.studyagent.assessment.entity.*;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.assessment.service.*;
import com.studyagent.common.exception.*;
import com.studyagent.onboarding.entity.AssignmentEntity;
import com.studyagent.onboarding.mapper.OnboardingMapper;
import com.studyagent.onboarding.service.AssignmentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentReviewServiceTest {
    @Mock AssessmentMapper mapper;
    @Mock OnboardingMapper onboarding;
    @Mock AssessmentSupport support;
    @Mock AssignmentService assignments;
    AssessmentReviewService service;
    @BeforeEach void setup() { service = new AssessmentReviewService(mapper,onboarding,support,assignments); }
    QuizAttemptEntity attempt() {
        var a=new QuizAttemptEntity(); a.setId(9L); a.setProjectId(10L); a.setUserId(8L); a.setQuizId(11L);
        a.setStatus("REVIEW_PENDING"); a.setAutoScore(new BigDecimal("65.00")); return a;
    }
    AssessmentReviewEntity review(String type, Review request) {
        var r=new AssessmentReviewEntity(); r.setId(90L); r.setProjectId(10L); r.setTargetId(9L);
        r.setTargetType(type); r.setReviewerId(7L); r.setDecision(request.decision());
        r.setScoreOverride(request.scoreOverride()); r.setComment(request.comment()); return r;
    }
    @Test void quizReviewOverridesFinalScoreButPreservesAutomaticScore() {
        var a=attempt(); var q=new QuizEntity(); q.setKnowledgeSnapshotId(55L); a.setAssignmentTaskId(42L);
        var request=new Review("PASS",new BigDecimal("85.00"),"reviewed");
        when(mapper.attempt(9L)).thenReturn(a); when(mapper.reviewAttemptForUpdate(9L)).thenReturn(a);
        when(mapper.quizForAttempt(9L)).thenReturn(q);
        when(mapper.latestReview("QUIZ_ATTEMPT",9L)).thenReturn(null,review("QUIZ_ATTEMPT",request));
        when(mapper.reviewAttempt(9L,request.scoreOverride())).thenReturn(1);
        when(mapper.insertReview(any())).thenReturn(1);
        service.quiz(7L,9L,request);
        assertThat(a.getAutoScore()).isEqualByComparingTo("65");
        verify(mapper).reviewAttempt(9L,new BigDecimal("85.00"));
        verify(assignments).completeFromAssessment(8L,42L,"QUIZ",11L,9L,10L,55L,true);
        verify(support).lock(7L,10L,true);
    }
    @Test void drillReviewPreservesProgramScoreAndDoesNotAutoPassNeedsWork() {
        var run=new DrillRunEntity(); run.setProjectId(10L); run.setUserId(8L); run.setScenarioId(11L);
        run.setAssignmentTaskId(42L); run.setStatus("REVIEW_PENDING"); run.setProgramScore(new BigDecimal("90"));
        var scenario=new DrillScenarioEntity(); scenario.setKnowledgeSnapshotId(55L);
        var request=new Review("NEEDS_WORK",null,"unsafe action");
        when(mapper.run(9L)).thenReturn(run); when(mapper.reviewRunForUpdate(9L)).thenReturn(run);
        when(mapper.scenarioForRun(9L)).thenReturn(scenario);
        when(mapper.reviewRun(9L,run.getProgramScore())).thenReturn(1);
        when(mapper.latestReview("DRILL_RUN",9L)).thenReturn(null,review("DRILL_RUN",request));
        when(mapper.insertReview(any())).thenReturn(1);
        service.drill(7L,9L,request);
        assertThat(run.getProgramScore()).isEqualByComparingTo("90");
        verify(assignments).completeFromAssessment(8L,42L,"DRILL",11L,9L,10L,55L,false);
    }
    @Test void identicalReviewRetryReusesHistoryAndConflictingRetryIsRejected() {
        var a=attempt(); when(mapper.attempt(9L)).thenReturn(a); when(mapper.reviewAttemptForUpdate(9L)).thenReturn(a);
        var request=new Review("PASS",new BigDecimal("80.00"),"checked");
        when(mapper.latestReview("QUIZ_ATTEMPT",9L)).thenReturn(review("QUIZ_ATTEMPT",request));
        service.quiz(7L,9L,new Review("PASS",new BigDecimal("80.0"),"checked"));
        assertThatThrownBy(() -> service.quiz(7L,9L,new Review("NEEDS_WORK",null,"different"))).isInstanceOf(BusinessException.class);
        verify(mapper,never()).reviewAttempt(anyLong(),any()); verify(mapper,never()).insertReview(any());
    }
    @Test void membershipFailurePreventsAllReviewWrites() {
        when(mapper.attempt(9L)).thenReturn(attempt());
        doThrow(new BusinessException(ErrorCode.PROJECT_ROLE_INSUFFICIENT)).when(support).lock(7L,10L,true);
        assertThatThrownBy(() -> service.quiz(7L,9L,new Review("PASS",null,null))).isInstanceOf(BusinessException.class);
        verify(mapper,never()).reviewAttemptForUpdate(anyLong()); verifyNoInteractions(assignments);
    }
    @Test void unsubmittedQuizCannotBeReviewed() {
        var a=attempt(); a.setStatus("IN_PROGRESS");
        when(mapper.attempt(9L)).thenReturn(a); when(mapper.reviewAttemptForUpdate(9L)).thenReturn(a);
        assertThatThrownBy(() -> service.quiz(7L,9L,new Review("PASS",null,null))).isInstanceOf(BusinessException.class);
        verify(mapper,never()).insertReview(any());
    }
    @Test void assignmentReviewKeepsDecisionSeparateFromScores() {
        var a=new AssignmentEntity(); a.setProjectId(10L);
        var request=new Review("NEEDS_WORK",null,"more practice");
        when(onboarding.findAssignment(9L)).thenReturn(a);
        when(mapper.latestReview("ASSIGNMENT",9L)).thenReturn(null,review("ASSIGNMENT",request));
        when(mapper.insertReview(any())).thenReturn(1);
        service.assignment(7L,9L,request);
        verify(onboarding).lockAssignment(9L); verify(assignments).review(7L,9L,"NEEDS_WORK");
        assertThatThrownBy(() -> service.assignment(7L,9L,new Review("PASS",BigDecimal.TEN,null))).isInstanceOf(BusinessException.class);
    }
}
