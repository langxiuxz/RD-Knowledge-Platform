package com.studyagent.ai.client;

import java.util.List;

public interface AiClient {
    boolean isHealthy();
    IndexResponse index(IndexRequest request);
    void deleteDocument(Long userId, Long knowledgeBaseId, Long documentId);
    void deleteKnowledgeBase(Long userId, Long knowledgeBaseId);
    List<RetrievedChunk> retrieve(RetrieveRequest request);

    record IndexRequest(Long userId, Long knowledgeBaseId, Long documentId, String taskId,
                        String fileUrl, String fileType, String fileName) {}
    record IndexResponse(String taskId) {}
    record RetrieveRequest(Long userId, Long knowledgeBaseId, String query, Integer topK,
                           Double scoreThreshold) {}
    record RetrievedChunk(String chunkId, Long documentId, String fileName, Integer page,
                          String content, Double score) {}
}
