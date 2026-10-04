package com.studyagent.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GenerateQuizRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 255) String topic,
        Long knowledgeBaseId,
        @Min(1) @Max(50) int questionCount,
        @NotBlank String difficulty,
        @NotEmpty List<@NotBlank String> types) {}
