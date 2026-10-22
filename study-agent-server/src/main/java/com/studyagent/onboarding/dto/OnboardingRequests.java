package com.studyagent.onboarding.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class OnboardingRequests {
    private OnboardingRequests() {}
    public record Path(@NotBlank @Size(max=128) String name, @Size(max=128) String targetRole,
                       @Size(max=1000) String description, @NotNull @Positive Long knowledgeSnapshotId) {}
    public record Task(@NotNull @Pattern(regexp="READ|QUIZ|DRILL|CHECKPOINT") String type,
                       @NotBlank @Size(max=128) String title, @Size(max=1000) String description,
                       @NotNull @Pattern(regexp="DOCUMENT_VERSION|QUIZ|DRILL|NONE") String resourceType,
                       @Positive Long resourceId,
                       @NotNull @Pattern(regexp="AUTO|MANUAL") String completionRule,
                       @Min(0) Integer estimatedMinutes, Boolean required, @Min(0) Integer sortOrder,
                       @Size(max=500) List<@NotNull @Positive Long> prerequisiteTaskIds) {
        public Task {
            estimatedMinutes = estimatedMinutes == null ? 0 : estimatedMinutes;
            required = required == null ? true : required;
            sortOrder = sortOrder == null ? 0 : sortOrder;
            prerequisiteTaskIds = prerequisiteTaskIds == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(prerequisiteTaskIds));
        }
    }
    public record Order(@NotNull @Size(min=1,max=500) List<@NotNull @Positive Long> taskIds) {}
    public record Assign(@NotNull @Size(min=1,max=100) List<@NotNull @Positive Long> learnerUserIds,
                         LocalDateTime dueAt) {}
    public record Complete(@Size(max=1000) String note) {}
}
