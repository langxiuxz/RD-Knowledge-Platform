package com.studyagent.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record KnowledgeBaseQuery(String name,
                                 @Pattern(regexp = "PROCESSING|READY|FAILED") String status,
                                 @Min(1) Integer page,
                                 @Min(1) @Max(100) Integer pageSize) {
    public KnowledgeBaseQuery {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
    }
}
