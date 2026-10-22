package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DrillEvidenceEntity {
    private Long id;
    private Long scenarioId;
    private String code;
    private String evidenceType;
    private String title;
    private String content;
    private String unlockRuleCode;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getScenarioId() { return scenarioId; }
    public void setScenarioId(Long value) { scenarioId = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String value) { evidenceType = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getUnlockRuleCode() { return unlockRuleCode; }
    public void setUnlockRuleCode(String value) { unlockRuleCode = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
}
