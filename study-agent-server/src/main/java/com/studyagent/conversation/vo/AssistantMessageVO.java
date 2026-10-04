package com.studyagent.conversation.vo;

import java.util.List;

public record AssistantMessageVO(Long id, String content, List<MessageCitationVO> citations) {}
