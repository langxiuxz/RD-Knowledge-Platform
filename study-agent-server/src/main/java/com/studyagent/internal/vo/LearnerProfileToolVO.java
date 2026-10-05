package com.studyagent.internal.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public record LearnerProfileToolVO(
        boolean hasData,
        List<Goal> currentGoals,
        List<Topic> topics,
        List<Topic> weakPoints,
        List<Topic> strengths,
        List<LearningActivity> recentLearning,
        QuizTrend quizTrend) {
    public record Goal(String title, String goal, LocalDate startDate, LocalDate endDate,
                       List<String> focusTopics, int completedTasks, int taskCount) {}
    public record Topic(String topic, BigDecimal mastery, BigDecimal quizAccuracy,
                        int studyMinutes, int wrongCount, int quizQuestionCount) {}
    public record LearningActivity(String type, String topic, int durationMinutes, LocalDateTime time) {}
    public record QuizTrend(String direction, BigDecimal recentAverage, BigDecimal previousAverage,
                            int recentQuizCount, int previousQuizCount) {}
}
