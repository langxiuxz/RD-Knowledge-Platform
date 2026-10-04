package com.studyagent.plan.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePlanTaskRequest(@NotNull Boolean completed) {}
