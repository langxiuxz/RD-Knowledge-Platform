package com.studyagent.internal.vo;

import java.math.BigDecimal;

public record LearningProgressToolVO(String topic, BigDecimal mastery, BigDecimal quizAccuracy,
                                     Integer studyMinutes, Integer wrongCount) {}
