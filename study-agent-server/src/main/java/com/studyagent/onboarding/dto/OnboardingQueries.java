package com.studyagent.onboarding.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public final class OnboardingQueries {
    private OnboardingQueries() {}
    public record Paths(@Size(max=128) String keyword,
                        @Pattern(regexp="DRAFT|PUBLISHED|ARCHIVED") String status,
                        @Size(max=128) String targetRole, @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
        public Paths { page=page==null?1:page; pageSize=pageSize==null?10:pageSize; }
    }
    public record Assignments(@Positive Long assigneeUserId,
                              @Pattern(regexp="ASSIGNED|IN_PROGRESS|REVIEW_PENDING|PASSED|NEEDS_WORK|CANCELLED") String status,
                              @Positive Long pathId,
                              @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueBefore,
                              @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
        public Assignments { page=page==null?1:page; pageSize=pageSize==null?10:pageSize; }
    }
}
