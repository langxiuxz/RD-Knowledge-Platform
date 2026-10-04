package com.studyagent.auth.vo;

import java.time.LocalDateTime;

public record UserProfileVO(Long id, String username, String nickname, String avatar, String email,
                            LocalDateTime createTime) {
}
