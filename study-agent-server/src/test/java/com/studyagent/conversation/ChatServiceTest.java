package com.studyagent.conversation;

import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.conversation.dto.SendMessageRequest;
import com.studyagent.conversation.entity.ConversationEntity;
import com.studyagent.conversation.service.ChatService;
import com.studyagent.conversation.service.ConversationService;
import com.studyagent.conversation.service.MessagePersistenceService;
import com.studyagent.conversation.vo.SendMessageVO;
import com.studyagent.generation.service.GenerationStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock ConversationService conversationService;
    @Mock MessagePersistenceService persistenceService;
    @Mock AiClient aiClient;
    @Mock GenerationStateService generationStateService;

    @Test
    void send_withRag_shouldPersistAnswerAndCitation() {
        ChatService service = service();
        ConversationEntity conversation = conversation(301L, 10L, "CHAT");
        AiClient.Citation citation = new AiClient.Citation("chunk-1", 101L, "notes.pdf", 12, 0.91);
        when(conversationService.requireOwned(1L, 301L)).thenReturn(conversation);
        when(persistenceService.createExchange(1L, 301L, "question"))
                .thenReturn(new MessagePersistenceService.MessageExchange(9000L, 9001L));
        when(aiClient.invoke(any())).thenReturn(
                new AiClient.AgentResponse("answer", List.of(citation), List.of(), "trace-1"));

        SendMessageVO result = service.send(1L, 301L, new SendMessageRequest(" question ", true, false));

        assertThat(result.userMessageId()).isEqualTo(9000L);
        assertThat(result.assistantMessage().content()).isEqualTo("answer");
        assertThat(result.assistantMessage().citations()).hasSize(1);
        assertThat(result.traceId()).isEqualTo("trace-1");
        ArgumentCaptor<AiClient.AgentRequest> request = ArgumentCaptor.forClass(AiClient.AgentRequest.class);
        verify(aiClient).invoke(request.capture());
        assertThat(request.getValue().mode()).isEqualTo("RAG");
        assertThat(request.getValue().userId()).isEqualTo(1L);
        assertThat(request.getValue().knowledgeBaseId()).isEqualTo(10L);
        verify(persistenceService).complete(9001L, 1L, "answer", "trace-1", "stop", List.of(citation));
    }

    @Test
    void send_withToolsAndKnowledgeBase_shouldUseAgentWithOwnedKnowledgeBase() {
        ChatService service = service();
        when(conversationService.requireOwned(1L, 301L)).thenReturn(conversation(301L, 10L, "CHAT"));
        when(persistenceService.createExchange(1L, 301L, "plan"))
                .thenReturn(new MessagePersistenceService.MessageExchange(9000L, 9001L));
        when(aiClient.invoke(any())).thenReturn(
                new AiClient.AgentResponse("answer", List.of(), List.of(), "trace-agent"));

        service.send(1L, 301L, new SendMessageRequest("plan", true, true));

        ArgumentCaptor<AiClient.AgentRequest> request = ArgumentCaptor.forClass(AiClient.AgentRequest.class);
        verify(aiClient).invoke(request.capture());
        assertThat(request.getValue().mode()).isEqualTo("AGENT");
        assertThat(request.getValue().knowledgeBaseId()).isEqualTo(10L);
    }

    @Test
    void stream_agentWithoutKnowledgeBase_shouldAllowToolsWithoutRag() throws Exception {
        ChatService service = service();
        when(conversationService.requireOwned(1L, 301L)).thenReturn(conversation(301L, null, "AGENT"));
        when(persistenceService.createExchange(1L, 301L, "plan"))
                .thenReturn(new MessagePersistenceService.MessageExchange(9000L, 9001L));
        doAnswer(invocation -> {
            AiClient.StreamEventConsumer consumer = invocation.getArgument(1);
            consumer.accept(new AiClient.StreamEvent("start", "{\"traceId\":\"trace-agent\"}"));
            consumer.accept(new AiClient.StreamEvent("done", "{\"traceId\":\"trace-agent\"}"));
            return null;
        }).when(aiClient).stream(any(), any());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.stream(1L, 301L, new SendMessageRequest("plan", false, true)).writeTo(output);

        ArgumentCaptor<AiClient.AgentRequest> request = ArgumentCaptor.forClass(AiClient.AgentRequest.class);
        verify(aiClient).stream(request.capture(), any());
        assertThat(request.getValue().mode()).isEqualTo("AGENT");
        assertThat(request.getValue().knowledgeBaseId()).isNull();
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("event: done");
    }

    @Test
    void stream_whenDone_shouldRelayEventsAndPersistOnce() throws Exception {
        ChatService service = service();
        when(conversationService.requireOwned(1L, 301L)).thenReturn(conversation(301L, 10L, "CHAT"));
        when(persistenceService.createExchange(1L, 301L, "hello"))
                .thenReturn(new MessagePersistenceService.MessageExchange(9000L, 9001L));
        doAnswer(invocation -> {
            AiClient.StreamEventConsumer consumer = invocation.getArgument(1);
            consumer.accept(new AiClient.StreamEvent("start", "{\"traceId\":\"trace-2\"}"));
            consumer.accept(new AiClient.StreamEvent("node", "{\"node\":\"router\",\"status\":\"completed\"}"));
            consumer.accept(new AiClient.StreamEvent("token", "{\"content\":\"hello \"}"));
            consumer.accept(new AiClient.StreamEvent("token", "{\"content\":\"world\"}"));
            consumer.accept(new AiClient.StreamEvent("citation",
                    "{\"chunkId\":\"chunk-2\",\"documentId\":102,\"fileName\":\"guide.pdf\",\"page\":3,\"score\":0.8}"));
            consumer.accept(new AiClient.StreamEvent("done", "{\"traceId\":\"trace-2\",\"finishReason\":\"stop\"}"));
            return null;
        }).when(aiClient).stream(any(), any());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.stream(1L, 301L, new SendMessageRequest("hello", false, false)).writeTo(output);

        String body = output.toString(StandardCharsets.UTF_8);
        assertThat(body).contains("event: start", "\"messageId\":9001", "event: token",
                "event: citation", "event: done");
        verify(persistenceService).bindTrace(9001L, 1L, "trace-2");
        verify(generationStateService).start("trace-2", 1L, 301L);
        ArgumentCaptor<List<AiClient.Citation>> citations = citationListCaptor();
        verify(persistenceService).complete(eq(9001L), eq(1L), eq("hello world"), eq("trace-2"),
                eq("stop"), citations.capture());
        assertThat(citations.getValue()).singleElement().satisfies(item -> {
            assertThat(item.documentId()).isEqualTo(102L);
            assertThat(item.chunkId()).isEqualTo("chunk-2");
        });
        verify(generationStateService).markDone("trace-2");
    }

    @Test
    void stop_whenAlreadyRequested_shouldBeIdempotent() {
        ChatService service = service();
        when(conversationService.requireOwned(1L, 301L)).thenReturn(conversation(301L, null, "CHAT"));
        when(generationStateService.get("trace-3"))
                .thenReturn(new GenerationStateService.GenerationState(1L, 301L, "CANCEL_REQUESTED"));

        service.stop(1L, 301L, "trace-3");

        verify(generationStateService, never()).requestCancel(any(), any(), any());
        verify(persistenceService, never()).cancel(any(), any(), any());
    }

    @Test
    void stop_withForeignGenerationState_shouldReject() {
        ChatService service = service();
        when(conversationService.requireOwned(1L, 301L)).thenReturn(conversation(301L, null, "CHAT"));
        when(generationStateService.get("trace-4"))
                .thenReturn(new GenerationStateService.GenerationState(2L, 301L, "RUNNING"));

        assertThatThrownBy(() -> service.stop(1L, 301L, "trace-4"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.GENERATION_CANCELLED));
        verify(generationStateService, never()).requestCancel(any(), any(), any());
    }

    private ChatService service() {
        return new ChatService(conversationService, persistenceService, aiClient, generationStateService,
                JsonMapper.builder().build());
    }

    private ConversationEntity conversation(Long id, Long knowledgeBaseId, String mode) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(id);
        conversation.setUserId(1L);
        conversation.setKnowledgeBaseId(knowledgeBaseId);
        conversation.setMode(mode);
        return conversation;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<AiClient.Citation>> citationListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
