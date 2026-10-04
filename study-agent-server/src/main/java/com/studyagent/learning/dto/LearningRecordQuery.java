package com.studyagent.learning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record LearningRecordQuery(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                  String type, String topic, @Min(1) int page,
                                  @Min(1) @Max(100) int pageSize) {}
