package com.studyagent.plan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record GenerateStudyPlanRequest(
        @NotBlank @Size(max = 500) String goal,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(1) @Max(1440) int dailyMinutes,
        @Size(max = 20) List<@NotBlank @Size(max = 128) String> focusTopics) {}
