package com.studyagent.conversation.vo;

import java.util.List;

public record MessagePageVO(List<MessageVO> rows, boolean hasMore) {
}
