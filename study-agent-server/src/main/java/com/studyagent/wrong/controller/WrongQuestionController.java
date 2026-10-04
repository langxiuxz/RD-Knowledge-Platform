package com.studyagent.wrong.controller;

import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.security.CurrentUser;
import com.studyagent.wrong.dto.UpdateMasteredRequest;
import com.studyagent.wrong.dto.WrongQuestionQuery;
import com.studyagent.wrong.service.WrongQuestionService;
import com.studyagent.wrong.vo.WrongQuestionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {
    private final WrongQuestionService service;

    public WrongQuestionController(WrongQuestionService service) { this.service = service; }

    @GetMapping
    public Result<PageResult<WrongQuestionVO>> list(@Valid WrongQuestionQuery query) {
        return Result.success(service.list(CurrentUser.requireId(), query));
    }

    @PutMapping("/{id}/mastered")
    public Result<Void> mastered(@PathVariable @Min(1) Long id, @Valid @RequestBody UpdateMasteredRequest request) {
        service.updateMastered(CurrentUser.requireId(), id, request.mastered());
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        service.delete(CurrentUser.requireId(), id);
        return Result.success(null);
    }
}
