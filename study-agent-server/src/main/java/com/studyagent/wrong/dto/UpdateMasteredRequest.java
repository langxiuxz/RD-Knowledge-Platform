package com.studyagent.wrong.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMasteredRequest(@NotNull Boolean mastered) {}
