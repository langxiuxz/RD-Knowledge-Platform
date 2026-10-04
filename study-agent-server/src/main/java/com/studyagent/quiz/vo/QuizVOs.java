package com.studyagent.quiz.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public final class QuizVOs {
    private QuizVOs() {}

    public record Generate(Long quizId, String status) {}
    public record Summary(Long id, String title, String difficulty, Integer questionCount, String status) {}
    public record Question(Long id, String type, String content, JsonNode options) {}
    public record Detail(Long id, String status, String errorMessage, List<Question> questions) {}
    public record Submit(BigDecimal score, int correctCount, int wrongCount, String analysis) {}
    public record ResultQuestion(Long questionId, JsonNode userAnswer, JsonNode correctAnswer,
                                 boolean correct, String explanation) {}
    public record Result(Long quizId, BigDecimal score, List<ResultQuestion> questions) {}
}
