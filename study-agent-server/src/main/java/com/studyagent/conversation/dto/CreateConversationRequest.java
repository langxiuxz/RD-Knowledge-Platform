package com.studyagent.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 128) String title,
        Long knowledgeBaseId,
        @NotBlank @Pattern(regexp = "CHAT|RAG|AGENT") String mode) {
}
