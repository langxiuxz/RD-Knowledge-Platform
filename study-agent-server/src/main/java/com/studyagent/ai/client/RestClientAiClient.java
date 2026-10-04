package com.studyagent.ai.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

@Component
public class RestClientAiClient implements AiClient {
    private final RestClient restClient;

    public RestClientAiClient(RestClient aiRestClient) {
        this.restClient = aiRestClient;
    }

    @Override
    public boolean isHealthy() {
        try {
            String body = restClient.get().uri("/internal/health")
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
            return body != null && body.contains("\"status\":\"UP\"");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public IndexResponse index(IndexRequest request) {
        IndexEnvelope envelope = restClient.post().uri("/internal/rag/index")
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .retrieve().body(IndexEnvelope.class);
        return new IndexResponse(envelope == null || envelope.data() == null ? null : envelope.data().taskId());
    }

    @Override
    public void deleteDocument(Long userId, Long knowledgeBaseId, Long documentId) {
        restClient.delete().uri(uriBuilder -> uriBuilder.path("/internal/rag/documents/{documentId}")
                        .queryParam("userId", userId).queryParam("knowledgeBaseId", knowledgeBaseId)
                        .build(documentId)).retrieve().toBodilessEntity();
    }

    @Override
    public void deleteKnowledgeBase(Long userId, Long knowledgeBaseId) {
        restClient.delete().uri(uriBuilder -> uriBuilder.path("/internal/rag/knowledge-bases/{knowledgeBaseId}")
                        .queryParam("userId", userId).build(knowledgeBaseId)).retrieve().toBodilessEntity();
    }

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        RetrieveEnvelope envelope = restClient.post().uri("/internal/rag/retrieve")
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .retrieve().body(RetrieveEnvelope.class);
        if (envelope == null || envelope.data() == null || envelope.data().chunks() == null) return List.of();
        return Arrays.stream(envelope.data().chunks()).map(c ->
                new RetrievedChunk(c.chunkId(), c.documentId(), c.fileName(), c.page(), c.content(), c.score())).toList();
    }

    private record IndexEnvelope(int code, String msg, IndexData data) {}
    private record IndexData(String taskId) {}
    private record RetrieveEnvelope(int code, String msg, RetrieveData data) {}
    private record RetrieveData(RetrieveChunk[] chunks) {}
    private record RetrieveChunk(String chunkId, Long documentId, String fileName, Integer page,
                                 String content, Double score) {}
}
