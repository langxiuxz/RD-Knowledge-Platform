package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DrillRuleEntity {
    private Long id;
    private Long scenarioId;
    private String code;
    private String knowledgeKey;
    private String title;
    private String ruleType;
    private String aliasesJson;
    private String sequenceGroup;
    private Integer minOrder;
    private BigDecimal points;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getScenarioId() { return scenarioId; }
    public void setScenarioId(Long value) { scenarioId = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getKnowledgeKey() { return knowledgeKey; }
    public void setKnowledgeKey(String value) { knowledgeKey = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String value) { ruleType = value; }
    public String getAliasesJson() { return aliasesJson; }
    public void setAliasesJson(String value) { aliasesJson = value; }
    public String getSequenceGroup() { return sequenceGroup; }
    public void setSequenceGroup(String value) { sequenceGroup = value; }
    public Integer getMinOrder() { return minOrder; }
    public void setMinOrder(Integer value) { minOrder = value; }
    public BigDecimal getPoints() { return points; }
    public void setPoints(BigDecimal value) { points = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
}
