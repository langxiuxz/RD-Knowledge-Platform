package com.studyagent.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgeBaseRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 500) String description,
        @Size(max = 128) String embeddingModel) {
}
