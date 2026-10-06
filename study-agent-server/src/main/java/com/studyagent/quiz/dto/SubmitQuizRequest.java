package com.studyagent.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record SubmitQuizRequest(@NotEmpty List<@NotNull @Valid Answer> answers) {
    // Keep the HTTP model mapper-neutral: Boot's Jackson 3 cannot deserialize Jackson 2 JsonNode.
    // QuizService converts these regular JSON values to its internal tree for type-specific grading.
    public record Answer(@NotNull @Positive Long questionId, @NotNull Object answer) {}
}
