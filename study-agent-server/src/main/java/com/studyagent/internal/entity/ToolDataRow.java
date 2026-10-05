package com.studyagent.internal.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ToolDataRow {
    private Long id;
    private Long questionId;
    private String title;
    private String topic;
    private String content;
    private String difficulty;
    private String status;
    private BigDecimal mastery;
    private BigDecimal quizAccuracy;
    private Integer studyMinutes;
    private Integer wrongCount;
    private Integer quizQuestionCount;
    private Boolean mastered;
    private BigDecimal score;
    private Integer correctCount;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
    public Boolean getMastered() { return mastered; }
    public void setMastered(Boolean mastered) { this.mastered = mastered; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
