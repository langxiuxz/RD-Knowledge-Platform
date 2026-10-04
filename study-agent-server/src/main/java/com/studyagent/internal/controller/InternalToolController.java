package com.studyagent.internal.controller;

import com.studyagent.common.result.Result;
import com.studyagent.internal.dto.InternalToolRequest;
import com.studyagent.internal.service.InternalToolService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tools")
public class InternalToolController {
    private final InternalToolService service;

    public InternalToolController(InternalToolService service) { this.service = service; }

    @PostMapping("/learning-progress")
    public Result<?> learningProgress(@Valid @RequestBody InternalToolRequest request) {
        return Result.success(service.learningProgress(request.userId(), request.limit()));
    }

    @PostMapping("/wrong-questions")
    public Result<?> wrongQuestions(@Valid @RequestBody InternalToolRequest request) {
        return Result.success(service.wrongQuestions(request.userId(), request.limit()));
    }

    @PostMapping("/weak-topics")
    public Result<?> weakTopics(@Valid @RequestBody InternalToolRequest request) {
        return Result.success(service.weakTopics(request.userId(), request.limit()));
    }

    @PostMapping("/recent-quiz-results")
    public Result<?> recentQuizResults(@Valid @RequestBody InternalToolRequest request) {
        return Result.success(service.recentQuizResults(request.userId(), request.limit()));
    }
}
