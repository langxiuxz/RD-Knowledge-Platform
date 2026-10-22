package com.studyagent.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long projectId,
        @Size(max = 128) String title,
        Long knowledgeBaseId,
        Long knowledgeSnapshotId,
        @NotBlank @Pattern(regexp = "CHAT|RAG|AGENT") String mode) {
}
