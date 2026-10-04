package com.studyagent.learning.controller;

import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.learning.dto.CreateLearningRecordRequest;
import com.studyagent.learning.dto.LearningRecordQuery;
import com.studyagent.learning.service.LearningService;
import com.studyagent.learning.vo.LearningVOs;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/learning")
public class LearningController {
    private final LearningService service;

    public LearningController(LearningService service) { this.service = service; }

    @PostMapping("/records")
    public Result<LearningVOs.Id> create(@Valid @RequestBody CreateLearningRecordRequest request) {
        return Result.success(service.create(CurrentUser.requireId(), request));
    }

    @GetMapping("/records")
    public Result<PageResult<LearningVOs.Record>> list(@Valid LearningRecordQuery query) {
        return Result.success(service.list(CurrentUser.requireId(), query));
    }

    @GetMapping("/progress")
    public Result<LearningVOs.Progress> progress(@RequestParam(required = false) String range) {
        return Result.success(service.progress(CurrentUser.requireId(), range));
    }
}
