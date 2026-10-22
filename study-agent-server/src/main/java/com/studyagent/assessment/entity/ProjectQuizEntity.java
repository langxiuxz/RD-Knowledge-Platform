package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProjectQuizEntity {
    private Long id;
    private Long projectId;
    private Long knowledgeSnapshotId;
    private Long revisionOfQuizId;
    private Integer versionNo;
    private String title;
    private String topic;
    private String difficulty;
    private Integer questionCount;
    private String typesJson;
    private String status;
    private Integer rubricVersion;
    private String rubricJson;
    private BigDecimal passScore;
    private String currentTaskId;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Long getKnowledgeSnapshotId() { return knowledgeSnapshotId; }
    public void setKnowledgeSnapshotId(Long value) { knowledgeSnapshotId = value; }
    public Long getRevisionOfQuizId() { return revisionOfQuizId; }
    public void setRevisionOfQuizId(Long value) { revisionOfQuizId = value; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer value) { versionNo = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getTopic() { return topic; }
    public void setTopic(String value) { topic = value; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String value) { difficulty = value; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer value) { questionCount = value; }
    public String getTypesJson() { return typesJson; }
    public void setTypesJson(String value) { typesJson = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getRubricVersion() { return rubricVersion; }
    public void setRubricVersion(Integer value) { rubricVersion = value; }
    public String getRubricJson() { return rubricJson; }
    public void setRubricJson(String value) { rubricJson = value; }
    public BigDecimal getPassScore() { return passScore; }
    public void setPassScore(BigDecimal value) { passScore = value; }
    public String getCurrentTaskId() { return currentTaskId; }
    public void setCurrentTaskId(String value) { currentTaskId = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
