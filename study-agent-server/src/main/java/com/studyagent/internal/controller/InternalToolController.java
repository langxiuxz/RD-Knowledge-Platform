package com.studyagent.internal.controller;

import com.studyagent.common.result.Result;
import com.studyagent.internal.dto.InternalToolRequest;
import com.studyagent.internal.dto.MemoryCandidateRequest;
import com.studyagent.internal.dto.MemoryQueryRequest;
import com.studyagent.internal.service.InternalMemoryService;
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
    private final InternalMemoryService memoryService;

    public InternalToolController(InternalToolService service, InternalMemoryService memoryService) {
        this.service = service;
        this.memoryService = memoryService;
    }

    @PostMapping("/learner-profile")
    public Result<?> learnerProfile(@Valid @RequestBody MemoryQueryRequest request) {
        return Result.success(service.learnerProfile(request.userId()));
    }

    @PostMapping("/memories/query")
    public Result<?> memories(@Valid @RequestBody MemoryQueryRequest request) {
        return Result.success(memoryService.memories(request));
    }

    @PostMapping("/memories/candidates")
    public Result<?> storeMemoryCandidate(@Valid @RequestBody MemoryCandidateRequest request) {
        return Result.success(memoryService.storeCandidate(request));
    }

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
