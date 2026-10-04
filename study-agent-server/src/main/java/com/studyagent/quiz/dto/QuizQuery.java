package com.studyagent.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record QuizQuery(String keyword, String status, @Min(1) int page, @Min(1) @Max(100) int pageSize) {}
