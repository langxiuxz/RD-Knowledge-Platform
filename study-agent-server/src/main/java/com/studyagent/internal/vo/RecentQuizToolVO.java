package com.studyagent.internal.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecentQuizToolVO(Long id, String title, String topic, String difficulty, String status,
                               BigDecimal score, Integer correctCount, Integer wrongCount, LocalDateTime createTime) {}
