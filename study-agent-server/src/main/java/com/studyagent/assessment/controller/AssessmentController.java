package com.studyagent.assessment.controller;

import com.studyagent.assessment.dto.AssessmentQuery;
import com.studyagent.assessment.dto.AssessmentRequests;
import com.studyagent.assessment.service.*;
import com.studyagent.assessment.vo.AssessmentVOs;
import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated
@RestController
public class AssessmentController {
    private final ProjectQuizService quizzes;
    private final QuizAttemptService attempts;
    private final DrillScenarioService scenarios;
    private final DrillRunService runs;

    public AssessmentController(ProjectQuizService quizzes, QuizAttemptService attempts,
                                DrillScenarioService scenarios, DrillRunService runs) {
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.scenarios = scenarios;
        this.runs = runs;
    }

    @PostMapping("/api/projects/{projectId}/quizzes/generate")
    public Result<AssessmentVOs.Generated> generate(@PathVariable @Min(1) Long projectId,
                                                    @Valid @RequestBody AssessmentRequests.Generate request) {
        return Result.success(quizzes.generate(CurrentUser.requireId(), projectId, request));
    }

    @GetMapping("/api/projects/{projectId}/quizzes")
    public Result<PageResult<AssessmentVOs.Quiz>> listQuizzes(@PathVariable @Min(1) Long projectId,
                                                              @Valid AssessmentQuery query) {
        return Result.success(quizzes.list(CurrentUser.requireId(), projectId, query));
    }

    @PutMapping("/api/quiz-questions/{id}")
    public Result<Void> updateQuestion(@PathVariable @Min(1) Long id,
                                       @Valid @RequestBody AssessmentRequests.Question request) {
        quizzes.updateQuestion(CurrentUser.requireId(), id, request);
        return Result.success();
    }

    @PostMapping("/api/quizzes/{id}/publish")
    public Result<Void> publishQuiz(@PathVariable @Min(1) Long id) {
        quizzes.publish(CurrentUser.requireId(), id);
        return Result.success();
    }

    @PostMapping("/api/quizzes/{id}/attempts")
    public Result<AssessmentVOs.Attempt> startAttempt(@PathVariable @Min(1) Long id,
            @Valid @RequestBody(required = false) AssessmentRequests.Start request) {
        return Result.success(attempts.start(CurrentUser.requireId(), id, request));
    }

    @PostMapping("/api/quiz-attempts/{id}/submit")
    public Result<AssessmentVOs.Attempt> submitAttempt(@PathVariable @Min(1) Long id,
            @Valid @RequestBody AssessmentRequests.SubmitQuiz request) {
        return Result.success(attempts.submit(CurrentUser.requireId(), id, request));
    }

    @GetMapping("/api/quiz-attempts/{id}")
    public Result<AssessmentVOs.Attempt> getAttempt(@PathVariable @Min(1) Long id) {
        return Result.success(attempts.get(CurrentUser.requireId(), id));
    }

    @GetMapping("/api/projects/{projectId}/drill-scenarios")
    public Result<List<AssessmentVOs.Scenario>> listScenarios(@PathVariable @Min(1) Long projectId,
            @RequestParam(required = false) String status) {
        return Result.success(scenarios.list(CurrentUser.requireId(), projectId, status));
    }

    @PostMapping("/api/projects/{projectId}/drill-scenarios")
    public Result<AssessmentVOs.Id> createScenario(@PathVariable @Min(1) Long projectId,
            @Valid @RequestBody AssessmentRequests.Scenario request) {
        return Result.success(scenarios.create(CurrentUser.requireId(), projectId, request));
    }

    @GetMapping("/api/drill-scenarios/{id}")
    public Result<AssessmentVOs.Scenario> getScenario(@PathVariable @Min(1) Long id) {
        return Result.success(scenarios.detail(CurrentUser.requireId(), id));
    }

    @PutMapping("/api/drill-scenarios/{id}")
    public Result<Void> updateScenario(@PathVariable @Min(1) Long id,
            @Valid @RequestBody AssessmentRequests.Scenario request) {
        scenarios.update(CurrentUser.requireId(), id, request);
        return Result.success();
    }

    @PostMapping("/api/drill-scenarios/{id}/publish")
    public Result<Void> publishScenario(@PathVariable @Min(1) Long id) {
        scenarios.publish(CurrentUser.requireId(), id);
        return Result.success();
    }

    @PostMapping("/api/drill-scenarios/{id}/runs")
    public Result<AssessmentVOs.RunStarted> startRun(@PathVariable @Min(1) Long id,
            @Valid @RequestBody(required = false) AssessmentRequests.Start request) {
        return Result.success(runs.start(CurrentUser.requireId(), id, request));
    }

    @PostMapping("/api/drill-runs/{id}/actions")
    public Result<AssessmentVOs.ActionAccepted> action(@PathVariable @Min(1) Long id,
            @Valid @RequestBody AssessmentRequests.Action request) {
        return Result.success(runs.action(CurrentUser.requireId(), id, request));
    }

    @PostMapping("/api/drill-runs/{id}/submit")
    public Result<AssessmentVOs.RunSubmitted> submitRun(@PathVariable @Min(1) Long id,
            @Valid @RequestBody AssessmentRequests.Conclusion request) {
        return Result.success(runs.submit(CurrentUser.requireId(), id, request));
    }

    @GetMapping("/api/drill-runs/{id}")
    public Result<AssessmentVOs.Run> getRun(@PathVariable @Min(1) Long id) {
        return Result.success(runs.get(CurrentUser.requireId(), id));
    }
}
