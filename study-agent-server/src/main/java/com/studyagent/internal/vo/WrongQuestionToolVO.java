package com.studyagent.internal.vo;

public record WrongQuestionToolVO(Long id, Long questionId, String topic, String content,
                                  Integer wrongCount, Boolean mastered) {}
