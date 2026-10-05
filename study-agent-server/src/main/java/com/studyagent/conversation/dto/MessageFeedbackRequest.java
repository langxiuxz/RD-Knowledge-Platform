package com.studyagent.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessageFeedbackRequest(
        @NotNull(message = "rating不能为空")
        Integer rating,
        @Size(max = 500, message = "reason不能超过500个字符")
        String reason) {
}
