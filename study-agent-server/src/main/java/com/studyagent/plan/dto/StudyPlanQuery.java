package com.studyagent.plan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StudyPlanQuery(String status, @Min(1) int page, @Min(1) @Max(100) int pageSize) {}
