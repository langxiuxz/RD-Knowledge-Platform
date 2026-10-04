package com.studyagent.knowledge.controller;

import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.knowledge.dto.CreateKnowledgeBaseRequest;
import com.studyagent.knowledge.dto.KnowledgeBaseQuery;
import com.studyagent.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import com.studyagent.knowledge.vo.KnowledgeBaseIdVO;
import com.studyagent.knowledge.vo.KnowledgeBaseVO;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) { this.service = service; }

    @GetMapping
    public Result<PageResult<KnowledgeBaseVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Positive Integer page,
            @RequestParam(defaultValue = "10") @Positive Integer pageSize) {
        return Result.success(service.list(CurrentUser.requireId(), new KnowledgeBaseQuery(name, status, page, pageSize)));
    }

    @PostMapping
    public Result<KnowledgeBaseIdVO> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return Result.success(service.create(CurrentUser.requireId(), request));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseVO> get(@PathVariable @Positive Long id) {
        return Result.success(service.get(CurrentUser.requireId(), id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        service.update(CurrentUser.requireId(), id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        service.delete(CurrentUser.requireId(), id);
        return Result.success();
    }
}
