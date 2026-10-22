package com.studyagent.knowledge.entity;

import java.time.LocalDateTime;

public class KnowledgeSnapshotEntity {
    private Long id;
    private Long projectId;
    private Long knowledgeBaseId;
    private Integer snapshotNo;
    private String reason;
    private Long createdBy;
    private Long itemCount;
    private LocalDateTime createTime;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long value) { knowledgeBaseId=value; }
    public Integer getSnapshotNo() { return snapshotNo; }
    public void setSnapshotNo(Integer value) { snapshotNo=value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason=value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy=value; }
    public Long getItemCount() { return itemCount; }
    public void setItemCount(Long value) { itemCount=value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { createTime=value; }
}
