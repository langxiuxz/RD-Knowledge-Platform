package com.studyagent.onboarding.controller;

import com.studyagent.common.result.*;
import com.studyagent.onboarding.dto.*;
import com.studyagent.onboarding.service.*;
import com.studyagent.onboarding.vo.OnboardingVOs;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OnboardingController {
    private final OnboardingPathService paths;
    private final AssignmentService assignments;
    public OnboardingController(OnboardingPathService paths,AssignmentService assignments) { this.paths=paths;this.assignments=assignments; }
    @GetMapping("/projects/{projectId}/onboarding-paths")
    public Result<PageResult<OnboardingVOs.Path>> list(@PathVariable @Positive Long projectId,@Valid OnboardingQueries.Paths query) {
        return Result.success(paths.list(CurrentUser.requireId(),projectId,query));
    }
    @PostMapping("/projects/{projectId}/onboarding-paths") @ResponseStatus(HttpStatus.CREATED)
    public Result<OnboardingVOs.Id> create(@PathVariable @Positive Long projectId,@Valid @RequestBody OnboardingRequests.Path request) {
        return Result.success(paths.create(CurrentUser.requireId(),projectId,request));
    }
    @GetMapping("/onboarding-paths/{id}")
    public Result<OnboardingVOs.PathDetail> get(@PathVariable @Positive Long id) {return Result.success(paths.get(CurrentUser.requireId(),id));}
    @PutMapping("/onboarding-paths/{id}")
    public Result<Void> update(@PathVariable @Positive Long id,@Valid @RequestBody OnboardingRequests.Path request) {
        paths.update(CurrentUser.requireId(),id,request);return Result.success();
    }
    @PostMapping("/onboarding-paths/{id}/tasks") @ResponseStatus(HttpStatus.CREATED)
    public Result<OnboardingVOs.Id> addTask(@PathVariable @Positive Long id,@Valid @RequestBody OnboardingRequests.Task request) {
        return Result.success(paths.addTask(CurrentUser.requireId(),id,request));
    }
    @PutMapping("/onboarding-path-tasks/{id}")
    public Result<Void> updateTask(@PathVariable @Positive Long id,@Valid @RequestBody OnboardingRequests.Task request) {
        paths.updateTask(CurrentUser.requireId(),id,request);return Result.success();
    }
    @DeleteMapping("/onboarding-path-tasks/{id}")
    public Result<Void> deleteTask(@PathVariable @Positive Long id) {paths.deleteTask(CurrentUser.requireId(),id);return Result.success();}
    @PutMapping("/onboarding-paths/{id}/task-order")
    public Result<Void> order(@PathVariable @Positive Long id,@Valid @RequestBody OnboardingRequests.Order request) {
        paths.order(CurrentUser.requireId(),id,request);return Result.success();
    }
    @PostMapping("/onboarding-paths/{id}/publish")
    public Result<Void> publish(@PathVariable @Positive Long id) {paths.publish(CurrentUser.requireId(),id);return Result.success();}
    @PostMapping("/onboarding-paths/{id}/assignments") @ResponseStatus(HttpStatus.CREATED)
    public Result<OnboardingVOs.AssignmentIds> assign(@PathVariable @Positive Long id,@Valid @RequestBody OnboardingRequests.Assign request) {
        return Result.success(assignments.assign(CurrentUser.requireId(),id,request));
    }
    @GetMapping("/projects/{projectId}/assignments")
    public Result<PageResult<OnboardingVOs.Assignment>> listAssignments(@PathVariable @Positive Long projectId,@Valid OnboardingQueries.Assignments query) {
        return Result.success(assignments.list(CurrentUser.requireId(),projectId,query));
    }
    @GetMapping("/assignments/{id}")
    public Result<OnboardingVOs.AssignmentDetail> assignment(@PathVariable @Positive Long id) {return Result.success(assignments.get(CurrentUser.requireId(),id));}
    @PostMapping("/assignments/{id}/start")
    public Result<Void> start(@PathVariable @Positive Long id) {assignments.start(CurrentUser.requireId(),id);return Result.success();}
    @PostMapping("/assignment-tasks/{id}/complete")
    public Result<Void> complete(@PathVariable @Positive Long id,@Valid @RequestBody OnboardingRequests.Complete request) {
        assignments.complete(CurrentUser.requireId(),id,request);return Result.success();
    }
    @PostMapping("/assignments/{id}/submit")
    public Result<Void> submit(@PathVariable @Positive Long id) {assignments.submit(CurrentUser.requireId(),id);return Result.success();}
}
