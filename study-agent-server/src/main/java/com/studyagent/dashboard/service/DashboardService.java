package com.studyagent.dashboard.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.dashboard.mapper.DashboardMapper;
import com.studyagent.dashboard.vo.DashboardVOs;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.learning.mapper.TopicProgressMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardService {
    private static final Set<String> RANGES = Set.of("7d", "30d", "all");
    private final DashboardMapper mapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final TopicProgressMapper progressMapper;

    public DashboardService(DashboardMapper mapper, KnowledgeBaseMapper knowledgeBaseMapper,
                            TopicProgressMapper progressMapper) {
        this.mapper = mapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.progressMapper = progressMapper;
    }

    public DashboardVOs.Overview overview(Long userId, String range) {
        LocalDateTime begin = rangeBegin(range);
        Integer minutes = mapper.sumStudyMinutes(userId, begin);
        Integer quizCount = mapper.countCompletedQuizzes(userId, begin);
        BigDecimal average = mapper.averageQuizScore(userId, begin);
        return new DashboardVOs.Overview(minutes == null ? 0 : minutes, quizCount == null ? 0 : quizCount,
                average == null ? BigDecimal.ZERO : average, knowledgeBaseMapper.countByUserId(userId, null, null),
                streak(mapper.findStudyDates(userId)));
    }

    public DashboardVOs.Trend trend(Long userId, LocalDate begin, LocalDate end) {
        if (begin == null || end == null || end.isBefore(begin) || begin.plusDays(366).isBefore(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "日期范围不合法");
        }
        Map<LocalDate, Integer> minutes = new HashMap<>();
        mapper.dailyStudy(userId, begin.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .forEach(row -> minutes.put(row.getDate(), row.getValue()));
        Map<LocalDate, BigDecimal> scores = new HashMap<>();
        mapper.dailyQuizScores(userId, begin.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .forEach(row -> scores.put(row.getDate(), row.getValue()));
        var dates = new ArrayList<LocalDate>();
        var study = new ArrayList<Integer>();
        var quiz = new ArrayList<BigDecimal>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(date);
            study.add(minutes.getOrDefault(date, 0));
            quiz.add(scores.getOrDefault(date, BigDecimal.ZERO));
        }
        return new DashboardVOs.Trend(dates, study, quiz);
    }

    public java.util.List<DashboardVOs.WeakTopic> weakTopics(Long userId, int limit) {
        if (limit < 1 || limit > 100) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "limit不合法");
        return progressMapper.findWeakByUserId(userId, limit).stream()
                .map(row -> new DashboardVOs.WeakTopic(row.getTopic(), row.getMastery(), row.getWrongCount()))
                .toList();
    }

    private LocalDateTime rangeBegin(String range) {
        String actual = range == null || range.isBlank() ? "all" : range;
        if (!RANGES.contains(actual)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "range不合法");
        return switch (actual) {
            case "7d" -> LocalDate.now().minusDays(6).atStartOfDay();
            case "30d" -> LocalDate.now().minusDays(29).atStartOfDay();
            default -> null;
        };
    }

    private int streak(java.util.List<LocalDate> dates) {
        Set<LocalDate> distinct = Set.copyOf(dates);
        LocalDate cursor = LocalDate.now();
        if (!distinct.contains(cursor)) cursor = cursor.minusDays(1);
        int streak = 0;
        while (distinct.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
