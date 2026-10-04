package com.studyagent.auth.vo;

public record RefreshTokenVO(String token, long expiresIn) {
}
