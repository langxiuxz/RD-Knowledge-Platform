package com.studyagent.plan.controller;

import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.plan.dto.GenerateStudyPlanRequest;
import com.studyagent.plan.dto.StudyPlanQuery;
import com.studyagent.plan.dto.UpdatePlanTaskRequest;
import com.studyagent.plan.service.StudyPlanService;
import com.studyagent.plan.vo.StudyPlanVOs;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/study-plans")
public class StudyPlanController {
    private final StudyPlanService service;

    public StudyPlanController(StudyPlanService service) { this.service = service; }

    @PostMapping("/generate")
    public Result<StudyPlanVOs.Generate> generate(@Valid @RequestBody GenerateStudyPlanRequest request) {
        return Result.success(service.generate(CurrentUser.requireId(), request));
    }

    @GetMapping
    public Result<PageResult<StudyPlanVOs.Summary>> list(@Valid StudyPlanQuery query) {
        return Result.success(service.list(CurrentUser.requireId(), query));
    }

    @GetMapping("/{id}")
    public Result<StudyPlanVOs.Detail> detail(@PathVariable @Min(1) Long id) {
        return Result.success(service.detail(CurrentUser.requireId(), id));
    }

    @PutMapping("/{planId}/tasks/{taskId}")
    public Result<Void> updateTask(@PathVariable @Min(1) Long planId,
                                   @PathVariable @Min(1) Long taskId,
                                   @Valid @RequestBody UpdatePlanTaskRequest request) {
        service.updateTask(CurrentUser.requireId(), planId, taskId, request.completed());
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        service.delete(CurrentUser.requireId(), id);
        return Result.success(null);
    }
}
