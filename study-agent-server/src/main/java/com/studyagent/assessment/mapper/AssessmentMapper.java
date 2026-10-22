package com.studyagent.assessment.mapper;

import com.studyagent.assessment.entity.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AssessmentMapper {
    int insertQuiz(ProjectQuizEntity quiz);
    int updateQuizStatus(@Param("id") Long id, @Param("taskId") String taskId,
                         @Param("from") String from, @Param("to") String to,
                         @Param("error") String error);
    int insertQuestions(@Param("questions") List<AssessmentQuestionEntity> questions);
    List<ProjectQuizEntity> quizzes(@Param("projectId") Long projectId, @Param("keyword") String keyword,
                                    @Param("status") String status, @Param("offset") long offset,
                                    @Param("limit") int limit);
    long countQuizzes(@Param("projectId") Long projectId, @Param("keyword") String keyword,
                      @Param("status") String status);
    ProjectQuizEntity quiz(@Param("id") Long id);
    ProjectQuizEntity lockQuiz(@Param("id") Long id);
    List<AssessmentQuestionEntity> questions(@Param("quizId") Long quizId);
    AssessmentQuestionEntity question(@Param("id") Long id);
    int updateQuestion(AssessmentQuestionEntity question);
    int publishQuiz(@Param("id") Long id);
    int insertContentDependency(@Param("projectId") Long projectId, @Param("targetType") String targetType,
                                @Param("targetId") Long targetId, @Param("versionId") Long versionId);
    long unresolvedDependencies(@Param("targetType") String targetType, @Param("targetId") Long targetId);
    int insertAttempt(QuizAttemptEntity attempt);
    QuizAttemptEntity lockAttempt(@Param("id") Long id);
    QuizAttemptEntity attempt(@Param("id") Long id);
    int insertAnswers(@Param("answers") List<AssessmentAnswerEntity> answers);
    List<AssessmentAnswerEntity> answers(@Param("attemptId") Long attemptId);
    int completeAttempt(QuizAttemptEntity attempt);
    int insertScenario(DrillScenarioEntity scenario);
    int insertRules(@Param("rules") List<DrillRuleEntity> rules);
    int insertEvidence(@Param("evidence") List<DrillEvidenceEntity> evidence);
    List<DrillScenarioEntity> scenarios(@Param("projectId") Long projectId, @Param("status") String status);
    DrillScenarioEntity scenario(@Param("id") Long id);
    DrillScenarioEntity lockScenario(@Param("id") Long id);
    int updateScenario(DrillScenarioEntity scenario);
    int deleteRules(@Param("scenarioId") Long scenarioId);
    int deleteEvidence(@Param("scenarioId") Long scenarioId);
    List<DrillRuleEntity> rules(@Param("scenarioId") Long scenarioId);
    List<DrillEvidenceEntity> evidence(@Param("scenarioId") Long scenarioId);
    int publishScenario(@Param("id") Long id);
    int insertRun(DrillRunEntity run);
    DrillRunEntity run(@Param("id") Long id);
    DrillRunEntity runForUpdate(@Param("id") Long id);
    DrillRunEntity lockRun(@Param("id") Long id);
    DrillRunActionEntity actionByRequest(@Param("runId") Long runId, @Param("requestId") String requestId);
    List<DrillRunActionEntity> actions(@Param("runId") Long runId);
    List<String> unlockedEvidenceCodes(@Param("runId") Long runId);
    int insertAction(DrillRunActionEntity action);
    int completeRun(DrillRunEntity run);
    QuizAttemptEntity reviewAttemptForUpdate(@Param("id") Long id);
    DrillRunEntity reviewRunForUpdate(@Param("id") Long id);
    ProjectQuizEntity quizForAttempt(@Param("attemptId") Long attemptId);
    DrillScenarioEntity scenarioForRun(@Param("runId") Long runId);
    int reviewAttempt(@Param("id") Long id, @Param("score") java.math.BigDecimal score);
    int reviewRun(@Param("id") Long id, @Param("score") java.math.BigDecimal score);
    int insertReview(AssessmentReviewEntity review);
    AssessmentReviewEntity latestReview(@Param("type") String type, @Param("id") Long id);
}
