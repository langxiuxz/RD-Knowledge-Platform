package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DrillRunEntity {
    private Long id;
    private Long projectId;
    private Long scenarioId;
    private Long userId;
    private Long assignmentTaskId;
    private String status;
    private Integer rubricVersion;
    private BigDecimal programScore;
    private BigDecimal finalScore;
    private Boolean passCandidate;
    private String conclusionJson;
    private String aiExplanation;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Long getScenarioId() { return scenarioId; }
    public void setScenarioId(Long value) { scenarioId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getAssignmentTaskId() { return assignmentTaskId; }
    public void setAssignmentTaskId(Long value) { assignmentTaskId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getRubricVersion() { return rubricVersion; }
    public void setRubricVersion(Integer value) { rubricVersion = value; }
    public BigDecimal getProgramScore() { return programScore; }
    public void setProgramScore(BigDecimal value) { programScore = value; }
    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal value) { finalScore = value; }
    public Boolean getPassCandidate() { return passCandidate; }
    public void setPassCandidate(Boolean value) { passCandidate = value; }
    public String getConclusionJson() { return conclusionJson; }
    public void setConclusionJson(String value) { conclusionJson = value; }
    public String getAiExplanation() { return aiExplanation; }
    public void setAiExplanation(String value) { aiExplanation = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
