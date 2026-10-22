package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AssessmentAnswerEntity {
    private Long id;
    private Long attemptId;
    private Long questionId;
    private String userAnswerJson;
    private BigDecimal ruleScore;
    private BigDecimal aiSuggestedScore;
    private BigDecimal finalScore;
    private Boolean isCorrect;
    private String scoringSource;
    private String aiFeedback;
    private String sourceRefsJson;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long value) { attemptId = value; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long value) { questionId = value; }
    public String getUserAnswerJson() { return userAnswerJson; }
    public void setUserAnswerJson(String value) { userAnswerJson = value; }
    public BigDecimal getRuleScore() { return ruleScore; }
    public void setRuleScore(BigDecimal value) { ruleScore = value; }
    public BigDecimal getAiSuggestedScore() { return aiSuggestedScore; }
    public void setAiSuggestedScore(BigDecimal value) { aiSuggestedScore = value; }
    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal value) { finalScore = value; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean value) { isCorrect = value; }
    public String getScoringSource() { return scoringSource; }
    public void setScoringSource(String value) { scoringSource = value; }
    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String value) { aiFeedback = value; }
    public String getSourceRefsJson() { return sourceRefsJson; }
    public void setSourceRefsJson(String value) { sourceRefsJson = value; }
}
