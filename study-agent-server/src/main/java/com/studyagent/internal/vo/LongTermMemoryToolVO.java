package com.studyagent.internal.vo;

import java.time.LocalDateTime;

public record LongTermMemoryToolVO(String category, String memoryKey, String value, LocalDateTime updatedAt) {}
