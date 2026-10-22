package com.studyagent.ai.client;

import java.util.List;
import java.util.Map;

public interface AiClient {
    boolean isHealthy();
    IndexResponse index(IndexRequest request);
    void deleteDocumentVersion(Long projectId, Long knowledgeBaseId, Long documentVersionId);
    void deleteKnowledgeBase(Long projectId, Long knowledgeBaseId);
    List<RetrievedChunk> retrieve(RetrieveRequest request);
    AgentResponse invoke(AgentRequest request);
    void stream(AgentRequest request, StreamEventConsumer consumer);
    QuizGenerateResponse generateQuiz(QuizGenerateRequest request);
    ShortGradeResponse gradeShort(ShortGradeRequest request);
    PlanGenerateResponse generatePlan(PlanGenerateRequest request);
    default DrillMatch normalizeDrillAction(DrillNormalizeRequest request) { return null; }
    default CapabilitySummary summarizeCapability(CapabilitySummaryRequest request) { return null; }

    record IndexRequest(Long projectId, Long knowledgeBaseId, Long documentId, Long documentVersionId, String taskId,
                        String fileUrl, String fileType, String fileName, String contentHash) {}
    record IndexResponse(String taskId) {}
    record RetrieveRequest(Long projectId, Long knowledgeBaseId, Long knowledgeSnapshotId, List<Long> documentVersionIds, String query, Integer topK,
                           Double scoreThreshold) {}
    record RetrievedChunk(String chunkId, Long documentId, Long documentVersionId, Long knowledgeSnapshotId, String fileName, Integer page,
                          String content, Double score) {}
    record AgentRequest(Long userId, Long conversationId, Long messageId, String content,
                        String mode, Long knowledgeBaseId, boolean stream,
                        List<ConversationMessage> recentMessages, Long projectId, Long knowledgeSnapshotId, List<Long> allowedDocumentVersionIds) {}
    record ConversationMessage(String role, String content) {}
    record Citation(String chunkId, Long documentId, String fileName, Integer page, Double score,
                    Long documentVersionId, Long knowledgeSnapshotId) {}
    record ToolCall(String name, String status, Object result, String error) {}
    record AgentResponse(String answer, List<Citation> citations, List<ToolCall> toolCalls, String traceId) {}
    record StreamEvent(String event, String data) {}
    record QuizGenerateRequest(Long userId, String topic, Long knowledgeBaseId, Integer questionCount,
                               String difficulty, List<String> types, Long projectId,
                               Long knowledgeSnapshotId, List<Long> documentVersionIds) {
        public QuizGenerateRequest(Long userId, String topic, Long knowledgeBaseId, Integer questionCount,
                                   String difficulty, List<String> types) {
            this(userId,topic,knowledgeBaseId,questionCount,difficulty,types,null,null,null);
        }
    }
    record QuizOption(String key, String text) {}
    record GeneratedQuestion(String type, String topic, String knowledgeKey, String content,
                             List<QuizOption> options, Object answer, String explanation,
                             List<Map<String, Object>> sourceRefs) {
        public GeneratedQuestion(String type,String topic,String content,List<QuizOption> options,Object answer,String explanation) {
            this(type,topic,null,content,options,answer,explanation,null);
        }
    }
    record QuizGenerateResponse(List<GeneratedQuestion> questions) {}
    record ShortGradeRequest(String question, String referenceAnswer, String userAnswer, Map<String,Object> rubric) {
        public ShortGradeRequest(String question,String referenceAnswer,String userAnswer) { this(question,referenceAnswer,userAnswer,null); }
    }
    record ShortGradeResponse(Double score, Boolean correct, String feedback, Double scoreSuggestion,List<String> evidence) {
        public ShortGradeResponse(Double score,Boolean correct,String feedback) { this(score,correct,feedback,null,null); }
    }
    record DrillCandidate(String code, String title, List<String> aliases) {}
    record DrillNormalizeRequest(Long scenarioId, String actionText, List<DrillCandidate> candidateRules) {}
    record DrillMatch(String normalizedAction, String matchedRuleCode, Double confidence, String explanation) {}
    record PlanGenerateRequest(Long userId, String goal, String startDate, String endDate,
                               Integer dailyMinutes, Map<String, Object> profile) {}
    record GeneratedPlanTask(String date, String topic, String type, Integer estimatedMinutes) {}
    record PlanGenerateResponse(String title, List<GeneratedPlanTask> tasks) {}
    record CapabilitySummaryRequest(Long assignmentId, Integer pathVersion, Long knowledgeSnapshotId,
                                   Map<String,Object> facts, List<RecommendedSource> allowedSources) {}
    record RecommendedSource(Long documentVersionId, String title) {}
    record CapabilitySummary(String summary, List<String> strengths, List<String> risks,
                             List<RecommendedSource> recommendedSources, String modelName) {}

    @FunctionalInterface
    interface StreamEventConsumer {
        void accept(StreamEvent event) throws Exception;
    }
}
