package com.studyagent.ai.client;

import java.util.List;
import java.util.Map;

public interface AiClient {
    boolean isHealthy();
    IndexResponse index(IndexRequest request);
    void deleteDocument(Long userId, Long knowledgeBaseId, Long documentId);
    void deleteKnowledgeBase(Long userId, Long knowledgeBaseId);
    List<RetrievedChunk> retrieve(RetrieveRequest request);
    AgentResponse invoke(AgentRequest request);
    void stream(AgentRequest request, StreamEventConsumer consumer);
    QuizGenerateResponse generateQuiz(QuizGenerateRequest request);
    ShortGradeResponse gradeShort(ShortGradeRequest request);
    PlanGenerateResponse generatePlan(PlanGenerateRequest request);

    record IndexRequest(Long userId, Long knowledgeBaseId, Long documentId, String taskId,
                        String fileUrl, String fileType, String fileName) {}
    record IndexResponse(String taskId) {}
    record RetrieveRequest(Long userId, Long knowledgeBaseId, String query, Integer topK,
                           Double scoreThreshold) {}
    record RetrievedChunk(String chunkId, Long documentId, String fileName, Integer page,
                          String content, Double score) {}
    record AgentRequest(Long userId, Long conversationId, Long messageId, String content,
                        String mode, Long knowledgeBaseId, boolean stream) {}
    record Citation(String chunkId, Long documentId, String fileName, Integer page, Double score) {}
    record ToolCall(String name, String status, Object result, String error) {}
    record AgentResponse(String answer, List<Citation> citations, List<ToolCall> toolCalls, String traceId) {}
    record StreamEvent(String event, String data) {}
    record QuizGenerateRequest(Long userId, String topic, Long knowledgeBaseId, Integer questionCount,
                               String difficulty, List<String> types) {}
    record QuizOption(String key, String text) {}
    record GeneratedQuestion(String type, String topic, String content, List<QuizOption> options,
                             Object answer, String explanation) {}
    record QuizGenerateResponse(List<GeneratedQuestion> questions) {}
    record ShortGradeRequest(String question, String referenceAnswer, String userAnswer) {}
    record ShortGradeResponse(Double score, Boolean correct, String feedback) {}
    record PlanGenerateRequest(Long userId, String goal, String startDate, String endDate,
                               Integer dailyMinutes, Map<String, Object> profile) {}
    record GeneratedPlanTask(String date, String topic, String type, Integer estimatedMinutes) {}
    record PlanGenerateResponse(String title, List<GeneratedPlanTask> tasks) {}

    @FunctionalInterface
    interface StreamEventConsumer {
        void accept(StreamEvent event) throws Exception;
    }
}
