package com.studyagent.assessment.dto;
import jakarta.validation.constraints.*;
public record AssessmentQuery(@Size(max=128) String keyword,
    @Pattern(regexp="GENERATING|DRAFT|READY|PUBLISHED|FAILED|ARCHIVED") String status,
    @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
    public AssessmentQuery { page=page==null?1:page; pageSize=pageSize==null?10:pageSize; }
}
