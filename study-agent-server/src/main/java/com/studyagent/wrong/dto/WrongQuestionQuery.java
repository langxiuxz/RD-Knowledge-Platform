package com.studyagent.wrong.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record WrongQuestionQuery(String topic, Boolean mastered, @Min(1) int page,
                                 @Min(1) @Max(100) int pageSize) {}
