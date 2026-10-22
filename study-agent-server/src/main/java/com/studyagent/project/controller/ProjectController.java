package com.studyagent.project.controller;

import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.project.dto.ProjectQueries;
import com.studyagent.project.dto.ProjectRequests;
import com.studyagent.project.service.ProjectService;
import com.studyagent.project.vo.ProjectVOs;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<PageResult<ProjectVOs.Summary>> list(@Valid ProjectQueries.Projects query) {
        return Result.success(projectService.list(CurrentUser.requireId(), query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ProjectVOs.Id> create(@Valid @RequestBody ProjectRequests.Create request) {
        return Result.success(projectService.create(CurrentUser.requireId(), request));
    }

    @GetMapping("/{id}")
    public Result<ProjectVOs.Project> get(@PathVariable @Positive Long id) {
        return Result.success(projectService.get(CurrentUser.requireId(), id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive Long id, @Valid @RequestBody ProjectRequests.Update request) {
        projectService.update(CurrentUser.requireId(), id, request);
        return Result.success();
    }

    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable @Positive Long id) {
        projectService.archive(CurrentUser.requireId(), id);
        return Result.success();
    }

    @GetMapping("/{id}/members")
    public Result<PageResult<ProjectVOs.Member>> members(@PathVariable @Positive Long id,
                                                       @Valid ProjectQueries.Members query) {
        return Result.success(projectService.members(CurrentUser.requireId(), id, query));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Void> addMember(@PathVariable @Positive Long id,
                                 @Valid @RequestBody ProjectRequests.AddMember request) {
        projectService.addMember(CurrentUser.requireId(), id, request);
        return Result.success();
    }

    @PutMapping("/{projectId}/members/{userId}")
    public Result<Void> changeRole(@PathVariable @Positive Long projectId, @PathVariable @Positive Long userId,
                                  @Valid @RequestBody ProjectRequests.ChangeRole request) {
        projectService.changeRole(CurrentUser.requireId(), projectId, userId, request);
        return Result.success();
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public Result<Void> removeMember(@PathVariable @Positive Long projectId, @PathVariable @Positive Long userId) {
        projectService.removeMember(CurrentUser.requireId(), projectId, userId);
        return Result.success();
    }

    @GetMapping("/{id}/audit-logs")
    public Result<PageResult<ProjectVOs.Audit>> auditLogs(@PathVariable @Positive Long id,
                                                        @Valid ProjectQueries.Audit query) {
        return Result.success(projectService.auditLogs(CurrentUser.requireId(), id, query));
    }
}
