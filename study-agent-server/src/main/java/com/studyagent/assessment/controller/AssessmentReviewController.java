package com.studyagent.assessment.controller;

import com.studyagent.assessment.dto.AssessmentRequests.Review;
import com.studyagent.assessment.service.AssessmentReviewService;
import com.studyagent.assessment.vo.ReviewVO;
import com.studyagent.common.result.Result;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class AssessmentReviewController {
    private final AssessmentReviewService reviews;
    public AssessmentReviewController(AssessmentReviewService reviews) { this.reviews=reviews; }
    @PostMapping("/api/quiz-attempts/{id}/review")
    public Result<ReviewVO> quiz(@PathVariable @Positive Long id,@Valid @RequestBody Review request) {
        return Result.success(reviews.quiz(CurrentUser.requireId(),id,request));
    }
    @PostMapping("/api/drill-runs/{id}/review")
    public Result<ReviewVO> drill(@PathVariable @Positive Long id,@Valid @RequestBody Review request) {
        return Result.success(reviews.drill(CurrentUser.requireId(),id,request));
    }
    @PostMapping("/api/assignments/{id}/review")
    public Result<ReviewVO> assignment(@PathVariable @Positive Long id,@Valid @RequestBody Review request) {
        return Result.success(reviews.assignment(CurrentUser.requireId(),id,request));
    }
}
