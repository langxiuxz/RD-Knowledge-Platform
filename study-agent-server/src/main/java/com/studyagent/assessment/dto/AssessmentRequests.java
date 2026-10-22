package com.studyagent.assessment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class AssessmentRequests {
    private AssessmentRequests() {}
    public record Generate(@NotBlank @Size(max=128) String title, @NotBlank @Size(max=255) String topic,
        @NotNull @Positive Long knowledgeSnapshotId, @NotNull @Min(1) @Max(50) Integer questionCount,
        @NotNull @Pattern(regexp="EASY|MEDIUM|HARD") String difficulty,
        @NotEmpty @Size(max=4) List<@Pattern(regexp="SINGLE|MULTIPLE|JUDGE|SHORT") String> types,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal passScore) {}
    public record Start(@Positive Long assignmentTaskId) {}
    public record Question(@Size(max=128) String knowledgeKey, @NotBlank @Size(max=20000) String content,
        JsonNode options, @NotNull JsonNode answer, @Size(max=20000) String explanation,
        @NotNull @DecimalMin("0.01") @DecimalMax("999.99") BigDecimal maxScore, JsonNode sourceRefs) {}
    public record Answer(@NotNull @Positive Long questionId, @NotNull JsonNode answer) {}
    public record SubmitQuiz(@NotEmpty @Size(max=50) List<@Valid Answer> answers) {}
    public record Dimension(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=128) String name,
        @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal maxScore) {}
    public record Rule(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String code,
        @Size(max=128) String knowledgeKey, @Size(max=128) String title,
        @NotNull @Pattern(regexp="REQUIRED|OPTIONAL|RISKY|VALIDATION") String type,
        @NotNull @DecimalMin("-100") @DecimalMax("100") BigDecimal points,
        @NotEmpty @Size(max=20) List<@NotBlank @Size(max=200) String> aliases,
        @Size(max=64) String sequenceGroup, @Min(1) Integer minOrder) {}
    public record Evidence(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String code,
        @NotNull @Pattern(regexp="LOG|METRIC|CONFIG|TRACE|HINT") String type,
        @NotBlank @Size(max=128) String title, @NotBlank @Size(max=20000) String content,
        @Size(max=64) String unlockRuleCode) {}
    public record Scenario(@NotBlank @Size(max=128) String name, @Size(max=1000) String description,
        @Size(max=16) String severity, @NotNull @Positive Long knowledgeSnapshotId,
        @NotBlank @Size(max=20000) String initialSymptom,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal passScore,
        @NotEmpty @Size(max=20) List<@Valid Dimension> rubric,
        @NotEmpty @Size(max=100) List<@Valid Rule> rules,
        @NotNull @Size(max=100) List<@Valid Evidence> evidence) {}
    public record Action(@NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String clientRequestId,
        @NotBlank @Size(max=2000) String actionText) {}
    public record Conclusion(@NotBlank @Size(max=10000) String rootCause,
        @NotBlank @Size(max=10000) String recoveryPlan, @NotBlank @Size(max=10000) String validationPlan,
        @NotNull @Size(max=100) List<@NotBlank @Size(max=64) String> evidenceCodes) {}
    public record Review(@NotNull @Pattern(regexp="PASS|NEEDS_WORK") String decision,
        @DecimalMin("0") @DecimalMax("100") BigDecimal scoreOverride, @Size(max=2000) String comment) {}
}
