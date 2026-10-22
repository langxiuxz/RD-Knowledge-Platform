package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AssessmentReviewEntity {
    private Long id;
    private Long projectId;
    private String targetType;
    private Long targetId;
    private Long reviewerId;
    private String decision;
    private BigDecimal scoreOverride;
    private String comment;
    private LocalDateTime createTime;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String value) { targetType = value; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long value) { targetId = value; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long value) { reviewerId = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { decision = value; }
    public BigDecimal getScoreOverride() { return scoreOverride; }
    public void setScoreOverride(BigDecimal value) { scoreOverride = value; }
    public String getComment() { return comment; }
    public void setComment(String value) { comment = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
}
