package com.studyagent.insight.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.insight.entity.CapabilityReportEntity;
import com.studyagent.insight.mapper.CapabilityReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CapabilityReportWorker {
    private static final Logger log = LoggerFactory.getLogger(CapabilityReportWorker.class);
    private static final String FAILURE = "能力报告生成失败，请重新发起";
    private final CapabilityReportMapper mapper;
    private final AiClient aiClient;
    private final ObjectMapper json;

    public CapabilityReportWorker(CapabilityReportMapper mapper, AiClient aiClient, ObjectMapper json) {
        this.mapper = mapper;
        this.aiClient = aiClient;
        this.json = json;
    }

    @Async("aiTaskExecutor")
    public void generate(Long reportId, String taskId) {
        try {
            CapabilityReportEntity report = mapper.find(reportId);
            if (report == null || !"PROCESSING".equals(report.getStatus())
                    || !Objects.equals(taskId, report.getCurrentTaskId())) return;
            Map<String, Object> facts = json.readValue(report.getFactsJson(), new TypeReference<>() {});
            List<Map<String, Object>> rawSources = json.convertValue(facts.get("allowedSources"), new TypeReference<>() {});
            List<AiClient.RecommendedSource> sources = rawSources.stream()
                    .map(row -> new AiClient.RecommendedSource(number(row.get("documentVersionId")),
                            Objects.toString(row.get("title"), "")))
                    .toList();
            AiClient.CapabilitySummary result = aiClient.summarizeCapability(new AiClient.CapabilitySummaryRequest(
                    report.getAssignmentId(), report.getPathVersion(), report.getKnowledgeSnapshotId(), facts, sources));
            if (result == null || result.summary() == null || result.summary().isBlank())
                throw new IllegalStateException("AI summary was empty");
            Map<Long, String> allowed = sources.stream().collect(Collectors.toMap(
                    AiClient.RecommendedSource::documentVersionId, AiClient.RecommendedSource::title));
            List<AiClient.RecommendedSource> recommended = result.recommendedSources() == null
                    ? List.of() : result.recommendedSources();
            for (AiClient.RecommendedSource source : recommended) {
                if (source == null || source.documentVersionId() == null
                        || !Objects.equals(allowed.get(source.documentVersionId()), source.title()))
                    throw new IllegalStateException("AI recommended a source outside the assignment snapshot");
            }
            int changed = mapper.complete(reportId, taskId, result.summary(), json.writeValueAsString(result.strengths()),
                    json.writeValueAsString(result.risks()), json.writeValueAsString(recommended), result.modelName());
            if (changed == 0) log.info("Discarded stale capability report result reportId={}", reportId);
        } catch (Exception exception) {
            mapper.fail(reportId, taskId);
            log.warn("Capability report generation failed reportId={} exception={}", reportId,
                    exception.getClass().getSimpleName());
        }
    }

    private static Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(value.toString());
    }
}
