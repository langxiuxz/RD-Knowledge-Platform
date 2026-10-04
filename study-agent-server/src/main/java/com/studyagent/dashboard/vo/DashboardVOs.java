package com.studyagent.dashboard.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardVOs {
    private DashboardVOs() {}
    public record Overview(int studyMinutes, int quizCount, BigDecimal averageScore,
                           long knowledgeBaseCount, int streakDays) {}
    public record Trend(List<LocalDate> dates, List<Integer> studyMinutes, List<BigDecimal> quizScores) {}
    public record WeakTopic(String topic, BigDecimal mastery, Integer wrongCount) {}
}
