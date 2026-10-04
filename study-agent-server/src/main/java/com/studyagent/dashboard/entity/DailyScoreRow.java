package com.studyagent.dashboard.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyScoreRow {
    private LocalDate date;
    private BigDecimal value;
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
}
