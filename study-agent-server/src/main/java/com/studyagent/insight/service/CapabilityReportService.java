package com.studyagent.insight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.assessment.service.AssessmentSupport;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.insight.entity.CapabilityReportEntity;
import com.studyagent.insight.mapper.CapabilityReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

@Service
public class CapabilityReportService {
    private final CapabilityReportMapper mapper;
    private final AssessmentSupport support;
    private final CapabilityReportWorker worker;
    private final ObjectMapper json;

    public CapabilityReportService(CapabilityReportMapper mapper, AssessmentSupport support,
                                   CapabilityReportWorker worker, ObjectMapper json) {
        this.mapper = mapper;
        this.support = support;
        this.worker = worker;
        this.json = json;
    }

    @Transactional
    public Map<String, Object> generate(Long userId, Long assignmentId) {
        Map<String, Object> initial = mapper.assignmentFacts(assignmentId);
        if (initial == null) throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND);
        Long projectId = number(initial.get("projectId"));
        support.lock(userId, projectId, true);
        Map<String, Object> assignment = mapper.assignmentFactsForUpdate(assignmentId);
        if (assignment == null) throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND);
        String status = Objects.toString(assignment.get("decisionStatus"), "");
        if (!Set.of("REVIEW_PENDING", "PASSED", "NEEDS_WORK").contains(status))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Assignment 尚未提交复核，不能生成能力报告");

        String decision = "PASSED".equals(status) ? "PASS" : "NEEDS_WORK".equals(status) ? "NEEDS_WORK" : null;
        CapabilityReportEntity latest = mapper.latestForAssignment(assignmentId);
        List<Map<String, Object>> tasks = mapper.taskFacts(assignmentId);
        List<Map<String, Object>> quizzes = mapper.quizFacts(assignmentId);
        List<Map<String, Object>> drills = mapper.drillFacts(assignmentId);
        List<Map<String, Object>> reviews = mapper.reviewFacts(projectId, assignmentId);
        List<Map<String, Object>> sources = mapper.snapshotSources(number(assignment.get("knowledgeSnapshotId")));
        List<String> missing = tasks.stream().filter(task -> truth(task.get("required")))
                .filter(task -> !"COMPLETED".equals(task.get("status")))
                .map(task -> Objects.toString(task.get("title"), "未完成任务")).toList();
        List<Map<String, Object>> risks = new ArrayList<>();
        quizzes.stream().filter(row -> numberOrZero(row.get("wrongCount")) + numberOrZero(row.get("shortLowCount")) > 0)
                .forEach(row -> risks.add(Map.of("type", "QUIZ_KNOWLEDGE_GAP", "attemptId", row.get("attemptId"),
                        "wrongAnswers", numberOrZero(row.get("wrongCount")),
                        "shortAnswersBelowThreshold", numberOrZero(row.get("shortLowCount")))));
        drills.stream().filter(row -> numberOrZero(row.get("riskyActions")) > 0)
                .forEach(row -> risks.add(Map.of("type", "RISKY_ACTION", "runId", row.get("runId"),
                        "count", row.get("riskyActions"))));
        drills.stream().filter(row -> numberOrZero(row.get("missingRequiredActions")) > 0)
                .forEach(row -> risks.add(Map.of("type", "MISSING_REQUIRED_ACTION", "runId", row.get("runId"),
                        "count", row.get("missingRequiredActions"))));
        quizzes.stream().filter(row -> "REVIEW_PENDING".equals(row.get("status")))
                .forEach(row -> risks.add(Map.of("type", "QUIZ_REVIEW_PENDING", "attemptId", row.get("attemptId"))));
        drills.stream().filter(row -> "REVIEW_PENDING".equals(row.get("status")))
                .forEach(row -> risks.add(Map.of("type", "DRILL_REVIEW_PENDING", "runId", row.get("runId"))));
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("assignment", assignment);
        facts.put("tasks", tasks);
        facts.put("quizAttempts", quizzes);
        facts.put("drillRuns", drills);
        facts.put("assessmentReviews", reviews);
        facts.put("riskItems", risks);
        facts.put("missingItems", missing);
        facts.put("managerDecision", decision);
        facts.put("allowedSources", sources);
        Map<String, Object> dimensions = Map.of("items", dimensions(quizzes, drills), "strengths", List.of());
        String factsJson = write(facts);
        if (latest != null && Objects.equals(latest.getOverallDecision(), decision)
                && Set.of("PROCESSING", "READY").contains(latest.getStatus())
                && sameFacts(latest.getFactsJson(), factsJson)) return view(latest, true);

        String taskId = UUID.randomUUID().toString();
        CapabilityReportEntity report = new CapabilityReportEntity();
        report.setProjectId(projectId);
        report.setAssignmentId(assignmentId);
        report.setUserId(number(assignment.get("userId")));
        report.setReportVersion(mapper.nextVersion(assignmentId));
        report.setKnowledgeSnapshotId(number(assignment.get("knowledgeSnapshotId")));
        report.setPathVersion(((Number) assignment.get("pathVersion")).intValue());
        report.setFactsJson(factsJson);
        report.setDimensionsJson(write(dimensions));
        report.setOverallDecision(decision);
        report.setCurrentTaskId(taskId);
        report.setStatus("PROCESSING");
        if (mapper.insert(report) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { worker.generate(report.getId(), taskId); }
        });
        return view(report, true);
    }

    public Map<String, Object> get(Long userId, Long reportId) {
        CapabilityReportEntity report = mapper.find(reportId);
        if (report == null) throw new BusinessException(ErrorCode.CAPABILITY_REPORT_NOT_FOUND);
        var member = supportMember(userId, report.getProjectId());
        if ("LEARNER".equals(member) && !Objects.equals(userId, report.getUserId()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        return view(report, false);
    }

    private String supportMember(Long userId, Long projectId) {
        return support.memberRole(userId, projectId);
    }

    private Map<String, Object> view(CapabilityReportEntity report, boolean generationResponse) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(generationResponse ? "reportId" : "id", report.getId());
        result.put("assignmentId", report.getAssignmentId());
        result.put("reportVersion", report.getReportVersion());
        result.put("status", report.getStatus());
        result.put("knowledgeSnapshotId", report.getKnowledgeSnapshotId());
        result.put("pathVersion", report.getPathVersion());
        result.put("overallDecision", report.getOverallDecision());
        result.put("summary", report.getAiSummary());
        result.put("modelName", report.getModelName());
        result.put("facts", readMap(report.getFactsJson()));
        Map<String, Object> dimensions = readMap(report.getDimensionsJson());
        result.put("dimensions", dimensions.getOrDefault("items", List.of()));
        result.put("strengths", dimensions.getOrDefault("strengths", List.of()));
        result.put("risks", readValue(report.getRisksJson()));
        result.put("recommendedSources", readValue(report.getRecommendedSourcesJson()));
        result.put("generatedAt", report.getGeneratedAt());
        result.put("errorMessage", report.getErrorMessage());
        return result;
    }

    private List<Map<String, Object>> dimensions(List<Map<String, Object>> quizzes, List<Map<String, Object>> drills) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : quizzes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", "QUIZ_ATTEMPT");
            item.put("score", row.get("finalScore"));
            item.put("automaticScore", row.get("autoScore"));
            item.put("status", row.get("status"));
            item.put("evidence", List.of("quizAttempt:" + row.get("attemptId")));
            result.add(item);
        }
        for (Map<String, Object> row : drills) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", "DRILL_RUN");
            item.put("score", row.get("finalScore"));
            item.put("programScore", row.get("programScore"));
            item.put("status", row.get("status"));
            item.put("evidence", List.of("drillRun:" + row.get("runId")));
            result.add(item);
        }
        return result;
    }

    private boolean sameFacts(String existing, String current) {
        if (existing == null) return false;
        try { return json.readTree(existing).equals(json.readTree(current)); }
        catch (JsonProcessingException exception) { return false; }
    }

    private Map<String, Object> readMap(String source) {
        if (source == null) return Map.of();
        try { return json.readValue(source, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { return Map.of(); }
    }

    private Object readValue(String source) {
        if (source == null) return List.of();
        try { return json.readValue(source, Object.class); }
        catch (JsonProcessingException exception) { return List.of(); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private static long numberOrZero(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static boolean truth(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() == 1;
    }
}
