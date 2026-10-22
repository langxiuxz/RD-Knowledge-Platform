package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DrillScenarioEntity {
    private Long id;
    private Long projectId;
    private Long knowledgeSnapshotId;
    private Long revisionOfScenarioId;
    private Integer versionNo;
    private String name;
    private String description;
    private String severity;
    private String initialSymptom;
    private String status;
    private Integer rubricVersion;
    private String rubricJson;
    private BigDecimal passScore;
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
    public Long getRevisionOfScenarioId() { return revisionOfScenarioId; }
    public void setRevisionOfScenarioId(Long value) { revisionOfScenarioId = value; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer value) { versionNo = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getSeverity() { return severity; }
    public void setSeverity(String value) { severity = value; }
    public String getInitialSymptom() { return initialSymptom; }
    public void setInitialSymptom(String value) { initialSymptom = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getRubricVersion() { return rubricVersion; }
    public void setRubricVersion(Integer value) { rubricVersion = value; }
    public String getRubricJson() { return rubricJson; }
    public void setRubricJson(String value) { rubricJson = value; }
    public BigDecimal getPassScore() { return passScore; }
    public void setPassScore(BigDecimal value) { passScore = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
