package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class QuizAttemptEntity {
    private Long id;
    private Long projectId;
    private Long quizId;
    private Long userId;
    private Long assignmentTaskId;
    private String status;
    private Integer rubricVersion;
    private BigDecimal autoScore;
    private BigDecimal finalScore;
    private Integer correctCount;
    private Boolean reviewRequired;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Long getQuizId() { return quizId; }
    public void setQuizId(Long value) { quizId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getAssignmentTaskId() { return assignmentTaskId; }
    public void setAssignmentTaskId(Long value) { assignmentTaskId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getRubricVersion() { return rubricVersion; }
    public void setRubricVersion(Integer value) { rubricVersion = value; }
    public BigDecimal getAutoScore() { return autoScore; }
    public void setAutoScore(BigDecimal value) { autoScore = value; }
    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal value) { finalScore = value; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer value) { correctCount = value; }
    public Boolean getReviewRequired() { return reviewRequired; }
    public void setReviewRequired(Boolean value) { reviewRequired = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
