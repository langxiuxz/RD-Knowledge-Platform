package com.studyagent.knowledge.vo;

public record KnowledgeBaseVO(Long id, String name, String description, Long documentCount,
                              Long chunkCount, String status) {
}
