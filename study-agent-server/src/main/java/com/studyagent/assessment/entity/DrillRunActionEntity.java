package com.studyagent.assessment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DrillRunActionEntity {
    private Long id;
    private Long runId;
    private Integer seqNo;
    private String clientRequestId;
    private String actionText;
    private String normalizedAction;
    private Long matchedRuleId;
    private String matchSource;
    private BigDecimal matchConfidence;
    private String evidenceUnlockedJson;
    private Boolean riskFlag;
    private BigDecimal pointsAwarded;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getRunId() { return runId; }
    public void setRunId(Long value) { runId = value; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer value) { seqNo = value; }
    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String value) { clientRequestId = value; }
    public String getActionText() { return actionText; }
    public void setActionText(String value) { actionText = value; }
    public String getNormalizedAction() { return normalizedAction; }
    public void setNormalizedAction(String value) { normalizedAction = value; }
    public Long getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(Long value) { matchedRuleId = value; }
    public String getMatchSource() { return matchSource; }
    public void setMatchSource(String value) { matchSource = value; }
    public BigDecimal getMatchConfidence() { return matchConfidence; }
    public void setMatchConfidence(BigDecimal value) { matchConfidence = value; }
    public String getEvidenceUnlockedJson() { return evidenceUnlockedJson; }
    public void setEvidenceUnlockedJson(String value) { evidenceUnlockedJson = value; }
    public Boolean getRiskFlag() { return riskFlag; }
    public void setRiskFlag(Boolean value) { riskFlag = value; }
    public BigDecimal getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(BigDecimal value) { pointsAwarded = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime = value; }
}
