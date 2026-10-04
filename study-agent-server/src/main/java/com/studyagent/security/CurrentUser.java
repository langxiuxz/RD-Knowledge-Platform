package com.studyagent.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Optional<Long> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal.userId());
        }
        return Optional.empty();
    }

    public static Optional<String> sessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.ofNullable(principal.sessionId());
        }
        return Optional.empty();
    }

    public static Long requireId() {
        return id().orElseThrow(() -> new com.studyagent.common.exception.BusinessException(
                com.studyagent.common.exception.ErrorCode.UNAUTHORIZED));
    }
}
