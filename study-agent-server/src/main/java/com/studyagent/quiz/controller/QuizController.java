package com.studyagent.quiz.controller;

import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.quiz.dto.GenerateQuizRequest;
import com.studyagent.quiz.dto.QuizQuery;
import com.studyagent.quiz.dto.SubmitQuizRequest;
import com.studyagent.quiz.service.QuizService;
import com.studyagent.assessment.service.ProjectQuizService;
import com.studyagent.quiz.vo.QuizVOs;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/quizzes")
public class QuizController {
    private final QuizService quizService;
    private final ProjectQuizService projectQuizzes;

    public QuizController(QuizService quizService) { this(quizService, null); }

    @Autowired
    public QuizController(QuizService quizService, ProjectQuizService projectQuizzes) {
        this.quizService = quizService;
        this.projectQuizzes = projectQuizzes;
    }

    @PostMapping("/generate")
    public Result<QuizVOs.Generate> generate(@Valid @RequestBody GenerateQuizRequest request) {
        return Result.success(quizService.generate(CurrentUser.requireId(), request));
    }

    @GetMapping
    public Result<PageResult<QuizVOs.Summary>> list(@Valid QuizQuery query) {
        return Result.success(quizService.list(CurrentUser.requireId(), query));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable @Min(1) Long id) {
        if (projectQuizzes != null && projectQuizzes.isProjectQuiz(id)) {
            return Result.success(projectQuizzes.detail(CurrentUser.requireId(), id));
        }
        return Result.success(quizService.detail(CurrentUser.requireId(), id));
    }

    @PostMapping("/{id}/submit")
    public Result<QuizVOs.Submit> submit(@PathVariable @Min(1) Long id,
                                        @Valid @RequestBody SubmitQuizRequest request) {
        return Result.success(quizService.submit(CurrentUser.requireId(), id, request));
    }

    @GetMapping("/{id}/result")
    public Result<QuizVOs.Result> result(@PathVariable @Min(1) Long id) {
        return Result.success(quizService.result(CurrentUser.requireId(), id));
    }
}
