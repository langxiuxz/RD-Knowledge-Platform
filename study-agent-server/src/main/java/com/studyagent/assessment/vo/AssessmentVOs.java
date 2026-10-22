package com.studyagent.assessment.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AssessmentVOs {
    private AssessmentVOs() {}
    public record Generated(Long quizId, String status) {}
    public record Id(Long id) {}
    public record ActionAccepted(Long actionId, String normalizedAction, List<Evidence> unlockedEvidence) {}
    public record RunStarted(Long runId, String status, String initialSymptom, List<Evidence> evidence) {}
    public record RunSubmitted(Long runId, String status, BigDecimal programScore, Boolean passCandidate,
        boolean reviewRequired) {}
    public record Quiz(Long id, Long projectId, Long knowledgeSnapshotId, String title, String topic,
        String difficulty, Integer questionCount, String status, Integer versionNo, Integer rubricVersion,
        BigDecimal passScore, String errorMessage, LocalDateTime updateTime) {}
    public record Question(Long id, String type, String knowledgeKey, String content, JsonNode options,
        JsonNode answer, String explanation, BigDecimal maxScore, JsonNode sourceRefs) {}
    public record QuizDetail(Quiz quiz, List<Question> questions) {}
    public record Answer(Long questionId, JsonNode userAnswer, BigDecimal ruleScore, BigDecimal aiSuggestedScore,
        BigDecimal finalScore, Boolean isCorrect, String scoringSource, String feedback, JsonNode sourceRefs) {}
    public record Attempt(Long attemptId, Long quizId, Long projectId, Long userId, Long assignmentTaskId,
        Long knowledgeSnapshotId, String status, Integer rubricVersion, BigDecimal autoScore, BigDecimal finalScore,
        Boolean reviewRequired, boolean passed, List<Question> questions, List<Answer> answers) {}
    public record Scenario(Long id, Long projectId, Long knowledgeSnapshotId, String name, String description,
        String severity, String initialSymptom, String status, Integer versionNo, Integer rubricVersion,
        BigDecimal passScore, JsonNode rubric, List<Rule> rules, List<Evidence> evidence) {}
    public record Rule(String code, String knowledgeKey, String title, String type, BigDecimal points,
        List<String> aliases, String sequenceGroup, Integer minOrder) {}
    public record Evidence(String code, String type, String title, String content, String unlockRuleCode) {}
    public record Action(Long actionId, Integer seqNo, String clientRequestId, String actionText,
        String normalizedAction, String matchSource, BigDecimal matchConfidence, Boolean riskFlag,
        BigDecimal pointsAwarded, List<Evidence> unlockedEvidence) {}
    public record ScoreItem(String code, String type, boolean matched, BigDecimal points) {}
    public record Run(Long runId, Long scenarioId, Long projectId, Long userId, Long assignmentTaskId,
        Long knowledgeSnapshotId, String status, Integer rubricVersion, String initialSymptom,
        List<Action> actions, List<Evidence> viewedEvidence, List<String> requiredActionsCompleted,
        List<String> missingRequiredActions, List<String> riskyActions, List<ScoreItem> scoreBreakdown,
        BigDecimal programScore, Boolean passCandidate, boolean reviewRequired, BigDecimal finalScore,
        JsonNode conclusion, String aiExplanation, String reviewDecision, BigDecimal scoreOverride) {}
}
