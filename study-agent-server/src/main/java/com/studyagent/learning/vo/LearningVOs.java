package com.studyagent.learning.vo;

import java.math.BigDecimal;
import java.util.List;

public final class LearningVOs {
    private LearningVOs() {}
    public record Id(Long id) {}
    public record Record(Long id, String type, String topic, Integer durationMinutes) {}
    public record Topic(String topic, BigDecimal mastery, BigDecimal quizAccuracy, Integer studyMinutes) {}
    public record Progress(List<Topic> topics) {}
}
