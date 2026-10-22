package com.studyagent.assessment.vo;

import com.studyagent.assessment.entity.AssessmentReviewEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewVO(Long id, String targetType, Long targetId, Long reviewerId, String decision,
                       BigDecimal scoreOverride, String comment, LocalDateTime createTime) {
    public static ReviewVO from(AssessmentReviewEntity review) {
        return review == null ? null : new ReviewVO(review.getId(),review.getTargetType(),review.getTargetId(),
            review.getReviewerId(),review.getDecision(),review.getScoreOverride(),review.getComment(),review.getCreateTime());
    }
}
