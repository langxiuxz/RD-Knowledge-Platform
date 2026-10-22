package com.studyagent.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyagent.assessment.entity.AssessmentQuestionEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import static com.studyagent.assessment.service.AssessmentSupport.invalid;

/** No model output participates in deterministic objective grading. */
public final class QuizScoring {
    private QuizScoring() {}
    public static void validate(AssessmentQuestionEntity q,JsonNode options,JsonNode answer) {
        if(q.getContent()==null || q.getContent().isBlank() || q.getContent().length()>20000 ||
            q.getMaxScore()==null || q.getMaxScore().signum()<=0 || q.getMaxScore().compareTo(new BigDecimal("999.99"))>0)
            throw invalid("题干或满分不合法");
        if(!Set.of("SINGLE","MULTIPLE","JUDGE","SHORT").contains(q.getType())) throw invalid("未知题型");
        validateAnswer(q.getType(),options,answer);
    }
    public static void validateAnswer(String type,JsonNode options,JsonNode answer) {
        if(answer==null || answer.isNull()) throw invalid("答案不能为空");
        if("SHORT".equals(type)) {
            if(!answer.isTextual() || answer.asText().isBlank() || answer.asText().length()>20000) throw invalid("简答题答案不合法");
        } else if("JUDGE".equals(type)) {
            if(!(answer.isBoolean() || answer.isTextual() && Set.of("true","false").contains(answer.asText().toLowerCase(Locale.ROOT))))
                throw invalid("判断题答案必须为 true/false");
        } else {
            if(options==null || !options.isArray() || options.size()<2 || options.size()>30) throw invalid("选项不合法");
            Set<String> keys=new HashSet<>();
            for(var option:options) {
                var key=option.get("key"); var text=option.get("text");
                if(key==null || !key.isTextual() || key.asText().isBlank() || key.asText().length()>16 ||
                    text==null || !text.isTextual() || text.asText().isBlank() || text.asText().length()>1000 || !keys.add(key.asText()))
                    throw invalid("选项 key 必须唯一，且包含正文");
            }
            if("SINGLE".equals(type)) {
                if(!answer.isTextual() || !keys.contains(answer.asText())) throw invalid("单选答案必须为有效选项 key");
            } else if("MULTIPLE".equals(type)) {
                Set<String> selected=new HashSet<>();
                if(!answer.isArray() || answer.isEmpty()) throw invalid("多选答案必须为非空数组");
                for(var value:answer) if(!value.isTextual() || !keys.contains(value.asText()) || !selected.add(value.asText()))
                    throw invalid("多选答案包含重复或未知选项");
            } else throw invalid("未知题型");
        }
    }
    public static boolean correct(String type,JsonNode expected,JsonNode actual) {
        if("MULTIPLE".equals(type)) return values(expected).equals(values(actual));
        if("JUDGE".equals(type)) return expected.asText().equalsIgnoreCase(actual.asText());
        return expected.equals(actual);
    }
    private static Set<String> values(JsonNode node) { Set<String> s=new HashSet<>();node.forEach(v->s.add(v.asText()));return s; }
    public static BigDecimal percentage(BigDecimal earned,BigDecimal total) {
        if(total.signum()<=0) throw invalid("评分总分必须大于0");
        return earned.multiply(BigDecimal.valueOf(100)).divide(total,2,RoundingMode.HALF_UP).max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }
}
