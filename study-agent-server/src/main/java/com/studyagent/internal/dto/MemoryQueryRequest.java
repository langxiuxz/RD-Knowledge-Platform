package com.studyagent.internal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MemoryQueryRequest(@NotNull @Positive Long userId,
                                 @Size(max = 1000) String query,
                                 @Min(1) @Max(20) Integer limit) {
    public int safeLimit() { return limit == null ? 20 : limit; }
}
