package com.studyagent.conversation.vo;

import java.util.List;

public record MessageVO(Long id, String role, String content, List<MessageCitationVO> citations) {
}
