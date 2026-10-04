package com.studyagent.dashboard.controller;

import com.studyagent.common.result.Result;
import com.studyagent.dashboard.service.DashboardService;
import com.studyagent.dashboard.vo.DashboardVOs;
import com.studyagent.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/overview")
    public Result<DashboardVOs.Overview> overview(@RequestParam(required = false) String range) {
        return Result.success(service.overview(CurrentUser.requireId(), range));
    }

    @GetMapping("/trend")
    public Result<DashboardVOs.Trend> trend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.success(service.trend(CurrentUser.requireId(), begin, end));
    }

    @GetMapping("/weak-topics")
    public Result<List<DashboardVOs.WeakTopic>> weakTopics(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(service.weakTopics(CurrentUser.requireId(), limit));
    }
}
