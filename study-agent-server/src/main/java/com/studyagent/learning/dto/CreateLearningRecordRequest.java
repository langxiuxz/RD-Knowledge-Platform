package com.studyagent.learning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;

public record CreateLearningRecordRequest(@NotBlank @Pattern(regexp = "READ|QUIZ|REVIEW|CHAT|FOCUS") String type, @NotBlank @Size(max = 128) String topic,
                                          @Min(0) @Max(1440) Integer durationMinutes, Long sourceId,
                                          @Size(max = 500) String remark,
                                          @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String clientRecordId,
                                          OffsetDateTime completedAt) {}
