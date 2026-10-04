package com.studyagent.wrong.entity;

import java.time.LocalDateTime;

public class WrongQuestionEntity {
    private Long id;
    private Long userId;
    private Long questionId;
    private String topic;
    private Integer wrongCount;
    private Boolean mastered;
    private LocalDateTime firstWrongAt;
    private LocalDateTime lastWrongAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public Boolean getMastered() { return mastered; }
    public void setMastered(Boolean mastered) { this.mastered = mastered; }
    public LocalDateTime getFirstWrongAt() { return firstWrongAt; }
    public void setFirstWrongAt(LocalDateTime firstWrongAt) { this.firstWrongAt = firstWrongAt; }
    public LocalDateTime getLastWrongAt() { return lastWrongAt; }
    public void setLastWrongAt(LocalDateTime lastWrongAt) { this.lastWrongAt = lastWrongAt; }
}
