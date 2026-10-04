package com.studyagent.plan.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StudyPlanEntity {
    private Long id;
    private Long userId;
    private String title;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer dailyMinutes;
    private String focusTopicsJson;
    private String generationStatus;
    private String status;
    private String errorMessage;
    private Integer taskCount;
    private Integer completedTaskCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getDailyMinutes() { return dailyMinutes; }
    public void setDailyMinutes(Integer dailyMinutes) { this.dailyMinutes = dailyMinutes; }
    public String getFocusTopicsJson() { return focusTopicsJson; }
    public void setFocusTopicsJson(String focusTopicsJson) { this.focusTopicsJson = focusTopicsJson; }
    public String getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(String generationStatus) { this.generationStatus = generationStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getTaskCount() { return taskCount; }
    public void setTaskCount(Integer taskCount) { this.taskCount = taskCount; }
    public Integer getCompletedTaskCount() { return completedTaskCount; }
    public void setCompletedTaskCount(Integer completedTaskCount) { this.completedTaskCount = completedTaskCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
