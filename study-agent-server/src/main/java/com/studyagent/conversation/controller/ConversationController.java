package com.studyagent.conversation.controller;

import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.conversation.dto.ConversationQuery;
import com.studyagent.conversation.dto.CreateConversationRequest;
import com.studyagent.conversation.dto.UpdateConversationRequest;
import com.studyagent.conversation.dto.SendMessageRequest;
import com.studyagent.conversation.service.ChatService;
import com.studyagent.conversation.service.ConversationService;
import com.studyagent.conversation.vo.ConversationIdVO;
import com.studyagent.conversation.vo.ConversationVO;
import com.studyagent.conversation.vo.MessagePageVO;
import com.studyagent.conversation.vo.SendMessageVO;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Validated
@RestController
public class ConversationController {
    private final ConversationService service;
    private final ChatService chatService;

    public ConversationController(ConversationService service, ChatService chatService) {
        this.service = service;
        this.chatService = chatService;
    }

    @PostMapping("/api/conversations")
    public Result<ConversationIdVO> create(@Valid @RequestBody CreateConversationRequest request) {
        return Result.success(service.create(CurrentUser.requireId(), request));
    }

    @GetMapping("/api/projects/{projectId}/conversations")
    public Result<PageResult<ConversationVO>> list(@PathVariable @Positive Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize) {
        return Result.success(service.list(CurrentUser.requireId(), projectId, new ConversationQuery(keyword, mode, page, pageSize)));
    }

    @GetMapping("/api/conversations/{id}")
    public Result<ConversationVO> get(@PathVariable @Positive Long id) {
        return Result.success(service.get(CurrentUser.requireId(), id));
    }

    @PutMapping("/api/conversations/{id}")
    public Result<Void> update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateConversationRequest request) {
        service.update(CurrentUser.requireId(), id, request);
        return Result.success();
    }

    @DeleteMapping("/api/conversations/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        service.delete(CurrentUser.requireId(), id);
        return Result.success();
    }

    @GetMapping("/api/conversations/{id}/messages")
    public Result<MessagePageVO> messages(@PathVariable @Positive Long id,
                                          @RequestParam(required = false) @Positive Long beforeId,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize) {
        return Result.success(service.messages(CurrentUser.requireId(), id, beforeId, pageSize));
    }

    @PostMapping("/api/conversations/{id}/messages")
    public Result<SendMessageVO> send(@PathVariable @Positive Long id,
                                      @Valid @RequestBody SendMessageRequest request) {
        return Result.success(chatService.send(CurrentUser.requireId(), id, request));
    }

    @PostMapping(value = "/api/conversations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable @Positive Long id,
                                                         @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .body(chatService.stream(CurrentUser.requireId(), id, request));
    }

    @DeleteMapping("/api/conversations/{id}/generation/{traceId}")
    public Result<Void> stop(@PathVariable @Positive Long id, @PathVariable String traceId) {
        chatService.stop(CurrentUser.requireId(), id, traceId);
        return Result.success();
    }
}
