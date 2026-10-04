package com.studyagent.internal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InternalToolRequest(
        @NotNull @Positive Long userId,
        @NotNull @Min(1) @Max(20) Integer limit) {
}
