package com.studyagent.insight.service;

import com.studyagent.assessment.service.AssessmentSupport;
import com.studyagent.insight.mapper.InsightMapper;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class InsightService {
    private final InsightMapper mapper;
    private final AssessmentSupport support;
    public InsightService(InsightMapper mapper,AssessmentSupport support) { this.mapper=mapper;this.support=support; }
    public Map<String,Object> overview(Long userId,Long projectId) {
        support.requireManager(userId,projectId);
        Map<String,Object> result = new LinkedHashMap<>(mapper.overview(projectId));
        result.put("knowledgeGapCount", mapper.knowledgeGaps(projectId).size());
        return result;
    }
    public List<Map<String,Object>> assignments(Long userId,Long projectId) {
        support.requireManager(userId,projectId);return mapper.assignments(projectId);
    }
    public Map<String,Object> knowledgeGaps(Long userId,Long projectId) {
        support.requireManager(userId,projectId);
        Map<String,List<Long>> documents = new HashMap<>();
        mapper.knowledgeGapDocuments(projectId).forEach(row -> documents
                .computeIfAbsent(Objects.toString(row.get("knowledgeKey")), ignored -> new ArrayList<>())
                .add(number(row.get("documentId"))));
        var rows=mapper.knowledgeGaps(projectId).stream().map(row->{
            double quizRate=rate(row.get("quizWrong"),row.get("quizTotal"));
            double shortRate=rate(row.get("shortLow"),row.get("shortTotal"));
            double drillMiss=rate(row.get("drillMiss"),row.get("drillRequired"));
            double riskRate=rate(row.get("riskHit"),row.get("riskTotal"));
            row.put("quizWrongRate",quizRate);row.put("drillMissRate",drillMiss);row.put("riskActionRate",riskRate);
            double highestRate=Math.max(Math.max(quizRate,shortRate),Math.max(drillMiss,riskRate));
            row.put("severity",highestRate>=0.5?"HIGH":highestRate>=0.25?"MEDIUM":"LOW");
            row.put("relatedDocumentIds", documents.getOrDefault(Objects.toString(row.get("knowledgeKey")), List.of()));
            return row;
        }).toList();
        return Map.of("rows",rows);
    }
    public List<Map<String,Object>> contentImpact(Long userId,Long projectId,String targetType,Long documentId,String status) {
        support.requireManager(userId,projectId);
        String filter = status == null ? "NEEDS_REVIEW" : status;
        if (!Set.of("NEEDS_REVIEW", "CONFIRMED", "RESOLVED").contains(filter))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,"unsupported content dependency status");
        if (targetType != null && !Set.of("QUIZ", "DRILL", "PATH").contains(targetType))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,"unsupported dependency target type");
        return mapper.contentImpact(projectId,targetType,documentId,filter);
    }
    public List<Map<String,Object>> pendingReviews(Long userId,Long projectId) {
        support.requireManager(userId,projectId);return mapper.pendingReviews(projectId);
    }
    private static double rate(Object numerator,Object denominator) {
        double n=numerator instanceof Number x?x.doubleValue():0;
        double d=denominator instanceof Number x?x.doubleValue():0;
        return d==0?0:BigDecimal.valueOf(n/d).setScale(4,java.math.RoundingMode.HALF_UP).doubleValue();
    }
    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }
}
