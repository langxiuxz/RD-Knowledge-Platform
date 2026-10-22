package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AssessmentQuestionEntity {
    private Long id;
    private Long quizId;
    private String knowledgeKey;
    private String type;
    private String content;
    private String optionsJson;
    private String correctAnswerJson;
    private String explanation;
    private String sourceRefsJson;
    private BigDecimal maxScore;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getQuizId() { return quizId; }
    public void setQuizId(Long value) { quizId = value; }
    public String getKnowledgeKey() { return knowledgeKey; }
    public void setKnowledgeKey(String value) { knowledgeKey = value; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String value) { optionsJson = value; }
    public String getCorrectAnswerJson() { return correctAnswerJson; }
    public void setCorrectAnswerJson(String value) { correctAnswerJson = value; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String value) { explanation = value; }
    public String getSourceRefsJson() { return sourceRefsJson; }
    public void setSourceRefsJson(String value) { sourceRefsJson = value; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal value) { maxScore = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
}
