package com.studyagent.quiz.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.quiz.dto.GenerateQuizRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class QuizGenerationWorker {
    private final AiClient aiClient;
    private final QuizGenerationPersistenceService persistenceService;

    public QuizGenerationWorker(AiClient aiClient, QuizGenerationPersistenceService persistenceService) {
        this.aiClient = aiClient;
        this.persistenceService = persistenceService;
    }

    @Async("aiTaskExecutor")
    public void generate(Long userId, Long quizId, GenerateQuizRequest request) {
        try {
            AiClient.QuizGenerateResponse response = aiClient.generateQuiz(new AiClient.QuizGenerateRequest(
                    userId, request.topic(), request.knowledgeBaseId(), request.questionCount(),
                    request.difficulty(), request.types()));
            persistenceService.complete(userId, quizId, response);
        } catch (RuntimeException exception) {
            persistenceService.fail(userId, quizId, exception.getMessage());
        }
    }
}
