package com.studyagent.quiz.vo;

import java.math.BigDecimal;
import java.util.List;

public final class QuizVOs {
    private QuizVOs() {}

    public record Generate(Long quizId, String status) {}
    public record Summary(Long id, String title, String difficulty, Integer questionCount, String status) {}
    /**
     * JSON fields are exposed as regular Java values rather than Jackson 2 JsonNode.
     * Spring Boot 4 uses its Jackson 3 HTTP mapper; keeping the response model mapper-neutral
     * prevents JsonNode implementation details from leaking into the API response.
     */
    public record Question(Long id, String type, String content, Object options) {}
    public record Detail(Long id, String status, String errorMessage, List<Question> questions) {}
    public record Submit(BigDecimal score, int correctCount, int wrongCount, String analysis) {}
    public record ResultQuestion(Long questionId, Object userAnswer, Object correctAnswer,
                                 boolean correct, String explanation) {}
    public record Result(Long quizId, BigDecimal score, List<ResultQuestion> questions) {}
}
