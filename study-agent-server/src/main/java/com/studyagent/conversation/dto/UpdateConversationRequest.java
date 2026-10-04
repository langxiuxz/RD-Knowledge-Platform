package com.studyagent.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(@NotBlank @Size(max = 128) String title) {
}
