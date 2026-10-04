package com.studyagent.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 10000) String content,
        Boolean useKnowledgeBase,
        Boolean enableTools) {
}
