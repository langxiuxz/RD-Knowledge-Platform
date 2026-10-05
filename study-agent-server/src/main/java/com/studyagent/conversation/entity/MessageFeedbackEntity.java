package com.studyagent.conversation.entity;

public class MessageFeedbackEntity {
    private Long id;
    private Long messageId;
    private Long userId;
    private Integer rating;
    private String reason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
