package com.studyagent.dashboard.entity;

import java.time.LocalDate;

public class DailyStudyRow {
    private LocalDate date;
    private Integer value;
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }
}
