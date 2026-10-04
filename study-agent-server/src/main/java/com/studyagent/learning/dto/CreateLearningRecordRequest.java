package com.studyagent.learning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLearningRecordRequest(@NotBlank String type, @NotBlank @Size(max = 128) String topic,
                                          @Min(0) @Max(1440) Integer durationMinutes, Long sourceId,
                                          @Size(max = 500) String remark) {}
