package com.studyagent.ai.client;

import java.util.List;

public interface AiClient {
    boolean isHealthy();
    IndexResponse index(IndexRequest request);
    void deleteDocument(Long userId, Long knowledgeBaseId, Long documentId);
    void deleteKnowledgeBase(Long userId, Long knowledgeBaseId);
    List<RetrievedChunk> retrieve(RetrieveRequest request);
    AgentResponse invoke(AgentRequest request);
    void stream(AgentRequest request, StreamEventConsumer consumer);

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

    @FunctionalInterface
    interface StreamEventConsumer {
        void accept(StreamEvent event) throws Exception;
    }
}
