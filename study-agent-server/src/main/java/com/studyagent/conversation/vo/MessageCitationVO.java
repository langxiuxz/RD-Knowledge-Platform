package com.studyagent.conversation.vo;

public record MessageCitationVO(Long documentId, String chunkId, String fileName, Integer page, Double score) {
}
