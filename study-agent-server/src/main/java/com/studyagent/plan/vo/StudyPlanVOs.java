package com.studyagent.plan.vo;

import java.time.LocalDate;
import java.util.List;

public final class StudyPlanVOs {
    private StudyPlanVOs() {}

    public record Generate(Long planId, String status) {}
    public record Summary(Long id, String title, String status, int progress) {}
    public record Task(Long id, LocalDate date, String topic, String type,
                       Integer estimatedMinutes, Boolean completed) {}
    public record Detail(Long id, String generationStatus, String status,
                         String errorMessage, List<Task> tasks) {}
}
