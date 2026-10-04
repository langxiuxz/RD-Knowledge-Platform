package com.studyagent.health.controller;

import com.studyagent.common.result.Result;
import com.studyagent.health.dto.HealthVO;
import com.studyagent.health.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public Result<HealthVO> health() {
        return Result.success(healthService.check());
    }
}
