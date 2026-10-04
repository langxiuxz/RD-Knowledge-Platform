package com.studyagent.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 32) String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 64) String nickname,
        @Email @Size(max = 128) String email) {
}
