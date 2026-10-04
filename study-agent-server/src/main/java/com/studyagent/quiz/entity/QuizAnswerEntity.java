package com.studyagent.quiz.entity;

public class QuizAnswerEntity {
    private Long id;
    private Long quizId;
    private Long questionId;
    private Long userId;
    private String userAnswerJson;
    private Boolean isCorrect;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuizId() { return quizId; }
    public void setQuizId(Long quizId) { this.quizId = quizId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserAnswerJson() { return userAnswerJson; }
    public void setUserAnswerJson(String userAnswerJson) { this.userAnswerJson = userAnswerJson; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean correct) { isCorrect = correct; }
}
