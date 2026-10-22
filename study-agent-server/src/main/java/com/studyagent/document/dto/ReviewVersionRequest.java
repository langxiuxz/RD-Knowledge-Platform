package com.studyagent.document.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ReviewVersionRequest(@NotBlank @Pattern(regexp="APPROVE|REJECT") String decision, @Size(max=1000) String comment) {}
