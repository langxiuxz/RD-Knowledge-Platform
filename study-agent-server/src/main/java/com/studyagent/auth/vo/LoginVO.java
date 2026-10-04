package com.studyagent.auth.vo;

public record LoginVO(UserSummaryVO user, String token, long expiresIn, String refreshToken,
                      long refreshExpiresIn) {
}
