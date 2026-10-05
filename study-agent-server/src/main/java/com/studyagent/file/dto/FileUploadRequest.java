package com.studyagent.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FileUploadRequest(@NotBlank @Pattern(regexp = "AVATAR|IMAGE") String type) { }
