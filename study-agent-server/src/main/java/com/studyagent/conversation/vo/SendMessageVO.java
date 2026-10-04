package com.studyagent.conversation.vo;

public record SendMessageVO(Long userMessageId, AssistantMessageVO assistantMessage, String traceId) {}
