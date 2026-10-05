package com.studyagent.internal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MemoryCandidateRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long conversationId,
        @NotNull @Positive Long messageId,
        @NotBlank @Pattern(regexp = "LEARNING_GOAL|EXPLANATION_PREFERENCE|LEARNING_STYLE") String category,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z_]+") String memoryKey,
        @NotBlank @Size(max = 500) String value,
        @NotBlank @Size(max = 1000) String evidenceText) {}
