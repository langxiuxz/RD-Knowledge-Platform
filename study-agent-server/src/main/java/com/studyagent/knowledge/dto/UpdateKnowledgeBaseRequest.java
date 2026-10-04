package com.studyagent.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateKnowledgeBaseRequest(@NotBlank @Size(max = 64) String name,
                                         @Size(max = 500) String description) {
}
