package com.studyagent.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProjectRequests {
    private ProjectRequests() {}

    public record Create(@NotBlank @Size(max = 128) String name,
                         @NotBlank @Size(max = 64) @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]*") String code,
                         @Size(max = 1000) String description) {}

    public record Update(@NotBlank @Size(max = 128) String name,
                         @NotBlank @Size(max = 64) @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]*") String code,
                         @Size(max = 1000) String description) {}

    public record AddMember(@NotBlank @Size(max = 32) String username,
                            @NotBlank @Pattern(regexp = "OWNER|MAINTAINER|LEARNER") String role) {}

    public record ChangeRole(@NotBlank @Pattern(regexp = "OWNER|MAINTAINER|LEARNER") String role) {}
}
