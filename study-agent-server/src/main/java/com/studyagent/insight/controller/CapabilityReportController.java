package com.studyagent.insight.controller;

import com.studyagent.common.result.Result;
import com.studyagent.insight.service.CapabilityReportService;
import com.studyagent.security.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Validated
public class CapabilityReportController {
    private final CapabilityReportService reports;
    public CapabilityReportController(CapabilityReportService reports) { this.reports = reports; }

    @PostMapping("/api/assignments/{id}/capability-report/generate")
    public ResponseEntity<Result<Map<String, Object>>> generate(@PathVariable @Positive Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Result.success(reports.generate(CurrentUser.requireId(), id)));
    }

    @GetMapping("/api/capability-reports/{id}")
    public Result<Map<String, Object>> get(@PathVariable @Positive Long id) {
        return Result.success(reports.get(CurrentUser.requireId(), id));
    }
}
