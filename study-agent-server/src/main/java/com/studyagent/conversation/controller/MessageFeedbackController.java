package com.studyagent.conversation.controller;

import com.studyagent.common.result.Result;
import com.studyagent.conversation.dto.MessageFeedbackRequest;
import com.studyagent.conversation.service.MessageFeedbackService;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/messages")
public class MessageFeedbackController {
    private final MessageFeedbackService service;

    public MessageFeedbackController(MessageFeedbackService service) {
        this.service = service;
    }

    @PostMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable @Positive Long id,
                                 @Valid @RequestBody MessageFeedbackRequest request) {
        service.save(CurrentUser.requireId(), id, request.rating(), request.reason());
        return Result.success();
    }
}
