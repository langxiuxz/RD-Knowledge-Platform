package com.studyagent.document.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DocumentProgressRequest(@NotBlank String taskId, @NotBlank String status,
                                      @Min(0) @Max(100) Integer progress,
                                      @Min(0) Integer chunkCount, String errorMessage) {}
