package com.studyagent.conversation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ConversationQuery(String keyword, String mode,
                                @Min(1) Integer page,
                                @Min(1) @Max(100) Integer pageSize) {
    public ConversationQuery {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
