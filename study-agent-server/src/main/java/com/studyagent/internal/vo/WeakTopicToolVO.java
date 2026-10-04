package com.studyagent.internal.vo;

import java.math.BigDecimal;

public record WeakTopicToolVO(String topic, BigDecimal mastery, BigDecimal quizAccuracy, Integer wrongCount) {}
