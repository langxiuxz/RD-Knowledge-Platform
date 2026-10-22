package com.studyagent.conversation.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.conversation.dto.SendMessageRequest;
import com.studyagent.conversation.entity.ConversationEntity;
import com.studyagent.conversation.entity.MessageEntity;
import com.studyagent.conversation.mapper.MessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.studyagent.conversation.vo.AssistantMessageVO;
import com.studyagent.conversation.vo.MessageCitationVO;
import com.studyagent.conversation.vo.SendMessageVO;
import com.studyagent.generation.service.GenerationStateService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatService {
    private final ConversationService conversationService;
    private final MessagePersistenceService persistenceService;
    private final AiClient aiClient;
    private final GenerationStateService generationStateService;
    private final ObjectMapper objectMapper;
    private final MessageMapper messageMapper;

    public ChatService(ConversationService conversationService, MessagePersistenceService persistenceService,
                       AiClient aiClient, GenerationStateService generationStateService, ObjectMapper objectMapper) {
        this(conversationService, persistenceService, aiClient, generationStateService, objectMapper, null);
    }

    @Autowired
    public ChatService(ConversationService conversationService, MessagePersistenceService persistenceService,
                       AiClient aiClient, GenerationStateService generationStateService, ObjectMapper objectMapper,
                       MessageMapper messageMapper) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.aiClient = aiClient;
        this.generationStateService = generationStateService;
        this.objectMapper = objectMapper;
        this.messageMapper = messageMapper;
    }

    public SendMessageVO send(Long userId, Long conversationId, SendMessageRequest request) {
        ConversationEntity conversation = conversationService.requireOwned(userId, conversationId);
        String mode = resolveMode(conversation, request);
        Long knowledgeBaseId = resolveKnowledgeBaseId(mode, conversation, request);
        validateKnowledgeBase(mode, request, knowledgeBaseId);
        if (knowledgeBaseId != null) conversationService.scope(userId, conversation);
        MessagePersistenceService.MessageExchange exchange =
                persistenceService.createExchange(userId, conversationId, request.content().trim());
        try {
            AiClient.AgentResponse response = aiClient.invoke(agentRequest(
                    userId, conversation, exchange.userMessageId(), request.content().trim(), mode,
                    knowledgeBaseId, false));
            validateCitations(conversation, response.citations(), userId);
            persistenceService.complete(exchange.assistantMessageId(), userId, response.answer(), response.traceId(),
                    "stop", response.citations());
            List<MessageCitationVO> citations = toCitationVOs(response.citations());
            return new SendMessageVO(exchange.userMessageId(),
                    new AssistantMessageVO(exchange.assistantMessageId(), response.answer(), citations), response.traceId());
        } catch (RuntimeException exception) {
            persistenceService.fail(exchange.assistantMessageId(), userId, "", null, exception.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    public StreamingResponseBody stream(Long userId, Long conversationId, SendMessageRequest request) {
        ConversationEntity conversation = conversationService.requireOwned(userId, conversationId);
        String mode = resolveMode(conversation, request);
        Long knowledgeBaseId = resolveKnowledgeBaseId(mode, conversation, request);
        validateKnowledgeBase(mode, request, knowledgeBaseId);
        if (knowledgeBaseId != null) conversationService.scope(userId, conversation);
        MessagePersistenceService.MessageExchange exchange =
                persistenceService.createExchange(userId, conversationId, request.content().trim());
        AiClient.AgentRequest aiRequest = agentRequest(
                userId, conversation, exchange.userMessageId(), request.content().trim(), mode,
                knowledgeBaseId, true);

        return output -> relay(output, userId, conversationId, exchange.assistantMessageId(), aiRequest);
    }

    public void stop(Long userId, Long conversationId, String traceId) {
        conversationService.requireOwned(userId, conversationId);
        GenerationStateService.GenerationState state = generationStateService.get(traceId);
        MessageEntity message = persistenceService.findByTrace(conversationId, userId, traceId);
        if (state != null && (!state.userId().equals(userId) || !state.conversationId().equals(conversationId))) {
            throw new BusinessException(ErrorCode.GENERATION_CANCELLED);
        }
        if (state != null && "CANCEL_REQUESTED".equals(state.status())) return;
        if (message != null && "CANCELLED".equals(message.getStatus())) return;
        if ((state != null && ("DONE".equals(state.status()) || "FAILED".equals(state.status())))
                || message == null || !"GENERATING".equals(message.getStatus())) {
            throw new BusinessException(ErrorCode.GENERATION_CANCELLED);
        }
        generationStateService.requestCancel(traceId, userId, conversationId);
        persistenceService.cancel(conversationId, userId, traceId);
    }

    private void relay(OutputStream output, Long userId, Long conversationId, Long assistantMessageId,
                       AiClient.AgentRequest aiRequest) throws IOException {
        StringBuilder content = new StringBuilder();
        List<AiClient.Citation> citations = new ArrayList<>();
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean(false);
        try {
            aiClient.stream(aiRequest, event -> {
                String currentTrace = traceId.get();
                if (currentTrace != null && generationStateService.isCancelRequested(currentTrace)) {
                    persistenceService.cancel(conversationId, userId, currentTrace);
                    writeEvent(output, "done", doneData(assistantMessageId, "cancelled"));
                    finished.set(true);
                    throw new CancellationSignal();
                }
                switch (event.event()) {
                    case "start" -> {
                        JsonNode data = objectMapper.readTree(event.data());
                        String receivedTrace = data.path("traceId").asText();
                        traceId.set(receivedTrace);
                        persistenceService.bindTrace(assistantMessageId, userId, receivedTrace);
                        generationStateService.start(receivedTrace, userId, conversationId);
                        ObjectNode start = objectMapper.createObjectNode();
                        start.put("traceId", receivedTrace);
                        start.put("messageId", assistantMessageId);
                        writeEvent(output, "start", objectMapper.writeValueAsString(start));
                    }
                    case "token" -> {
                        JsonNode data = objectMapper.readTree(event.data());
                        content.append(data.path("content").asText());
                        writeEvent(output, "token", event.data());
                    }
                    case "citation" -> {
                        var source = citation(event.data());
                        validateCitation(source, aiRequest.knowledgeSnapshotId(), aiRequest.allowedDocumentVersionIds());
                        citations.add(source);
                        writeEvent(output, "citation", objectMapper.writeValueAsString(toCitationVO(source)));
                    }
                    case "done" -> {
                        String id = traceId.get();
                        persistenceService.complete(assistantMessageId, userId, content.toString(), id,
                                "stop", citations);
                        if (id != null) generationStateService.markDone(id);
                        writeEvent(output, "done", doneData(assistantMessageId, "stop"));
                        finished.set(true);
                    }
                    case "error" -> {
                        String id = traceId.get();
                        persistenceService.fail(assistantMessageId, userId, content.toString(), id, "AI service error");
                        if (id != null) generationStateService.markFailed(id);
                        writeEvent(output, "error", event.data());
                        finished.set(true);
                    }
                    default -> writeEvent(output, event.event(), event.data());
                }
            });
            if (!finished.get()) {
                failStream(output, assistantMessageId, userId, traceId.get(), content.toString());
            }
        } catch (CancellationSignal ignored) {
            // Cancellation is a normal terminal state.
        } catch (Exception exception) {
            if (!finished.get()) failStream(output, assistantMessageId, userId, traceId.get(), content.toString());
        }
    }

    private void failStream(OutputStream output, Long assistantMessageId, Long userId,
                            String traceId, String partialContent) throws IOException {
        persistenceService.fail(assistantMessageId, userId, partialContent, traceId, "AI service error");
        if (traceId != null) generationStateService.markFailed(traceId);
        writeEvent(output, "error", "{\"code\":3001,\"message\":\"AI服务不可用\"}");
    }

    private AiClient.Citation citation(String json) throws Exception {
        JsonNode data = objectMapper.readTree(json);
        return new AiClient.Citation(data.path("chunkId").asText(), data.path("documentId").asLong(),
                data.path("fileName").asText(), data.path("page").isNull() ? null : data.path("page").asInt(),
                data.path("score").isNull() ? null : data.path("score").asDouble(),
                data.path("documentVersionId").asLong(), data.path("knowledgeSnapshotId").asLong());
    }

    private String doneData(Long messageId, String finishReason) throws Exception {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("messageId", messageId);
        data.put("finishReason", finishReason);
        return objectMapper.writeValueAsString(data);
    }

    private void writeEvent(OutputStream output, String event, String data) throws IOException {
        output.write(("event: " + event + "\n" + "data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private String resolveMode(ConversationEntity conversation, SendMessageRequest request) {
        if (Boolean.TRUE.equals(request.enableTools())) return "AGENT";
        if (Boolean.TRUE.equals(request.useKnowledgeBase())) return "RAG";
        return conversation.getMode();
    }

    private Long resolveKnowledgeBaseId(String mode, ConversationEntity conversation, SendMessageRequest request) {
        if ("RAG".equals(mode) || ("AGENT".equals(mode) && Boolean.TRUE.equals(request.useKnowledgeBase()))) {
            return conversation.getKnowledgeBaseId();
        }
        return null;
    }

    private void validateKnowledgeBase(String mode, SendMessageRequest request, Long knowledgeBaseId) {
        boolean requested = "RAG".equals(mode)
                || ("AGENT".equals(mode) && Boolean.TRUE.equals(request.useKnowledgeBase()));
        if (requested && knowledgeBaseId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "启用知识库需要先为会话绑定知识库");
        }
    }

    private AiClient.AgentRequest agentRequest(Long userId, ConversationEntity conversation, Long messageId,
                                                String content, String mode, Long knowledgeBaseId, boolean stream) {
        List<AiClient.ConversationMessage> recent = new ArrayList<>();
        if (messageMapper != null) {
            messageMapper.findRecentCompletedContext(conversation.getId(), userId, messageId, 8).stream()
                    .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                    .map(message -> new AiClient.ConversationMessage(message.getRole(),
                            message.getContent().length() > 2000 ? message.getContent().substring(0, 2000) : message.getContent()))
                    .forEach(recent::add);
            java.util.Collections.reverse(recent);
        }
        return new AiClient.AgentRequest(userId, conversation.getId(), messageId, content, mode,
                knowledgeBaseId, stream, recent, conversation.getProjectId(),
                knowledgeBaseId == null ? null : conversation.getKnowledgeSnapshotId(),
                knowledgeBaseId == null ? List.of() : conversationService.scope(userId, conversation).allowedDocumentVersionIds());
    }

    private List<MessageCitationVO> toCitationVOs(List<AiClient.Citation> citations) {
        if (citations == null) return List.of();
        return citations.stream().map(this::toCitationVO).toList();
    }

    private MessageCitationVO toCitationVO(AiClient.Citation source) {
        return new MessageCitationVO(source.documentId(), source.chunkId(), source.fileName(), source.page(),
                source.score(), source.documentVersionId(), source.knowledgeSnapshotId());
    }

    private void validateCitations(ConversationEntity c, List<AiClient.Citation> citations, Long userId) {
        if (citations == null || citations.isEmpty()) return;
        var scope = conversationService.scope(userId, c);
        for (var citation : citations) validateCitation(citation, scope.knowledgeSnapshotId(), scope.allowedDocumentVersionIds());
    }

    private void validateCitation(AiClient.Citation citation, Long snapshotId, List<Long> allowedVersionIds) {
        if (!java.util.Objects.equals(citation.knowledgeSnapshotId(), snapshotId)
                || !allowedVersionIds.contains(citation.documentVersionId()))
            throw new IllegalStateException("Citation outside snapshot");
    }

    private static class CancellationSignal extends RuntimeException {}
}
