package com.studyagent.learning.entity;

import java.math.BigDecimal;

public class TopicProgressEntity {
    private Long id;
    private Long userId;
    private String topic;
    private BigDecimal mastery;
    private BigDecimal quizAccuracy;
    private Integer studyMinutes;
    private Integer wrongCount;
    private Integer quizQuestionCount;
    private Integer correctQuestionCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public BigDecimal getMastery() { return mastery; }
    public void setMastery(BigDecimal mastery) { this.mastery = mastery; }
    public BigDecimal getQuizAccuracy() { return quizAccuracy; }
    public void setQuizAccuracy(BigDecimal quizAccuracy) { this.quizAccuracy = quizAccuracy; }
    public Integer getStudyMinutes() { return studyMinutes; }
    public void setStudyMinutes(Integer studyMinutes) { this.studyMinutes = studyMinutes; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public Integer getQuizQuestionCount() { return quizQuestionCount; }
    public void setQuizQuestionCount(Integer quizQuestionCount) { this.quizQuestionCount = quizQuestionCount; }
    public Integer getCorrectQuestionCount() { return correctQuestionCount; }
    public void setCorrectQuestionCount(Integer correctQuestionCount) { this.correctQuestionCount = correctQuestionCount; }
}
