package com.studyagent.insight.controller;

import com.studyagent.common.result.Result;
import com.studyagent.insight.service.InsightService;
import com.studyagent.security.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/projects/{projectId}/insights")
public class InsightController {
    private final InsightService insights;
    public InsightController(InsightService insights) { this.insights=insights; }
    @GetMapping("/overview")
    public Result<Map<String,Object>> overview(@PathVariable @Positive Long projectId) {
        return Result.success(insights.overview(CurrentUser.requireId(),projectId));
    }
    @GetMapping("/assignments")
    public Result<List<Map<String,Object>>> assignments(@PathVariable @Positive Long projectId) {
        return Result.success(insights.assignments(CurrentUser.requireId(),projectId));
    }
    @GetMapping("/knowledge-gaps")
    public Result<Map<String,Object>> gaps(@PathVariable @Positive Long projectId) {
        return Result.success(insights.knowledgeGaps(CurrentUser.requireId(),projectId));
    }
    @GetMapping("/content-impact")
    public Result<List<Map<String,Object>>> contentImpact(@PathVariable @Positive Long projectId,
        @RequestParam(required=false) String targetType,@RequestParam(required=false) @Positive Long documentId,
        @RequestParam(required=false) String status) {
        return Result.success(insights.contentImpact(CurrentUser.requireId(),projectId,targetType,documentId,status));
    }
    @GetMapping("/pending-reviews")
    public Result<List<Map<String,Object>>> pendingReviews(@PathVariable @Positive Long projectId) {
        return Result.success(insights.pendingReviews(CurrentUser.requireId(),projectId));
    }
}
