package com.studyagent.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

public final class ProjectQueries {
    private ProjectQueries() {}

    public record Projects(String keyword,
                           @Pattern(regexp = "ACTIVE|ARCHIVED") String status,
                           @Pattern(regexp = "OWNER|MAINTAINER|LEARNER") String role,
                           @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
        public Projects { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; }
    }

    public record Members(String keyword,
                          @Pattern(regexp = "OWNER|MAINTAINER|LEARNER") String role,
                          @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
        public Members { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; }
    }

    public record Audit(String action, String resourceType, @Min(1) Long userId,
                        LocalDateTime startTime, LocalDateTime endTime,
                        @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
        public Audit { page = page == null ? 1 : page; pageSize = pageSize == null ? 10 : pageSize; }
    }
}
