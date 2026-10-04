package com.studyagent.ai.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    @Override
    public AgentResponse invoke(AgentRequest request) {
        AgentEnvelope envelope = restClient.post().uri("/internal/agent/invoke")
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .retrieve().body(AgentEnvelope.class);
        if (envelope == null || envelope.data() == null) {
            throw new IllegalStateException("AI service returned an empty response");
        }
        AgentData data = envelope.data();
        List<Citation> citations = data.citations() == null ? List.of() : Arrays.asList(data.citations());
        List<ToolCall> toolCalls = data.toolCalls() == null ? List.of() : Arrays.asList(data.toolCalls());
        return new AgentResponse(data.answer(), citations, toolCalls, data.traceId());
    }

    @Override
    public void stream(AgentRequest request, StreamEventConsumer consumer) {
        restClient.post().uri("/internal/agent/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(request)
                .exchange((clientRequest, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("AI stream failed with status " + response.getStatusCode());
                    }
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String eventName = "message";
                        StringBuilder data = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty()) {
                                if (!data.isEmpty()) {
                                    accept(consumer, new StreamEvent(eventName, data.toString()));
                                    data.setLength(0);
                                    eventName = "message";
                                }
                            } else if (line.startsWith("event:")) {
                                eventName = line.substring(6).trim();
                            } else if (line.startsWith("data:")) {
                                if (!data.isEmpty()) data.append('\n');
                                data.append(line.substring(5).trim());
                            }
                        }
                        if (!data.isEmpty()) accept(consumer, new StreamEvent(eventName, data.toString()));
                    }
                    return null;
                });
    }

    private void accept(StreamEventConsumer consumer, StreamEvent event) {
        try {
            consumer.accept(event);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to consume AI stream event", exception);
        }
    }

    private record IndexEnvelope(int code, String msg, IndexData data) {}
    private record IndexData(String taskId) {}
    private record RetrieveEnvelope(int code, String msg, RetrieveData data) {}
    private record RetrieveData(RetrieveChunk[] chunks) {}
    private record RetrieveChunk(String chunkId, Long documentId, String fileName, Integer page,
                                 String content, Double score) {}
    private record AgentEnvelope(int code, String msg, AgentData data) {}
    private record AgentData(String answer, Citation[] citations, ToolCall[] toolCalls, String traceId) {}
}
