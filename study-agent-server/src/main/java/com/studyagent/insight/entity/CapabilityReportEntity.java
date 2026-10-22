package com.studyagent.insight.entity;

import java.time.LocalDateTime;

public class CapabilityReportEntity {
    private Long id,projectId,assignmentId,userId,knowledgeSnapshotId;
    private Integer reportVersion,pathVersion;
    private String status,factsJson,dimensionsJson,aiSummary,risksJson,recommendedSourcesJson,overallDecision,
        currentTaskId,modelName,errorMessage;
    private LocalDateTime generatedAt,createTime,updateTime;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getProjectId(){return projectId;} public void setProjectId(Long v){projectId=v;}
    public Long getAssignmentId(){return assignmentId;} public void setAssignmentId(Long v){assignmentId=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getKnowledgeSnapshotId(){return knowledgeSnapshotId;} public void setKnowledgeSnapshotId(Long v){knowledgeSnapshotId=v;}
    public Integer getReportVersion(){return reportVersion;} public void setReportVersion(Integer v){reportVersion=v;}
    public Integer getPathVersion(){return pathVersion;} public void setPathVersion(Integer v){pathVersion=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getFactsJson(){return factsJson;} public void setFactsJson(String v){factsJson=v;}
    public String getDimensionsJson(){return dimensionsJson;} public void setDimensionsJson(String v){dimensionsJson=v;}
    public String getAiSummary(){return aiSummary;} public void setAiSummary(String v){aiSummary=v;}
    public String getRisksJson(){return risksJson;} public void setRisksJson(String v){risksJson=v;}
    public String getRecommendedSourcesJson(){return recommendedSourcesJson;} public void setRecommendedSourcesJson(String v){recommendedSourcesJson=v;}
    public String getOverallDecision(){return overallDecision;} public void setOverallDecision(String v){overallDecision=v;}
    public String getCurrentTaskId(){return currentTaskId;} public void setCurrentTaskId(String v){currentTaskId=v;}
    public String getModelName(){return modelName;} public void setModelName(String v){modelName=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public LocalDateTime getGeneratedAt(){return generatedAt;} public void setGeneratedAt(LocalDateTime v){generatedAt=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
}
