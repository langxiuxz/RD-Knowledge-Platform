package com.studyagent.internal.service;

import com.studyagent.internal.entity.ToolDataRow;
import com.studyagent.internal.mapper.ToolDataMapper;
import com.studyagent.internal.vo.LearningProgressToolVO;
import com.studyagent.internal.vo.RecentQuizToolVO;
import com.studyagent.internal.vo.WeakTopicToolVO;
import com.studyagent.internal.vo.WrongQuestionToolVO;
import com.studyagent.internal.vo.LearnerProfileToolVO;
import com.studyagent.learning.entity.LearningRecordEntity;
import com.studyagent.plan.entity.StudyPlanEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Comparator;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class InternalToolService {
    private final ToolDataMapper mapper;
    private final ObjectMapper objectMapper;

    public InternalToolService(ToolDataMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public LearnerProfileToolVO learnerProfile(Long userId) {
        List<ToolDataRow> progress = mapper.findLearningProgress(userId, 50);
        List<ToolDataRow> quizzes = mapper.findRecentQuizResults(userId, 10);
        List<LearningRecordEntity> records = mapper.findRecentLearningForProfile(userId, 10);
        List<StudyPlanEntity> plans = mapper.findActivePlansForProfile(userId, 5);
        List<LearnerProfileToolVO.Topic> topics = progress.stream().map(this::topic).toList();
        List<LearnerProfileToolVO.Topic> weak = progress.stream()
                .sorted(Comparator.comparing(ToolDataRow::getMastery,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ToolDataRow::getWrongCount,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3).map(this::topic).toList();
        List<LearnerProfileToolVO.Topic> strengths = progress.stream()
                .filter(row -> row.getMastery() != null)
                .sorted(Comparator.comparing(ToolDataRow::getMastery).reversed())
                .limit(3).map(this::topic).toList();
        List<LearnerProfileToolVO.LearningActivity> activities = records.stream().map(record ->
                new LearnerProfileToolVO.LearningActivity(record.getType(), record.getTopic(),
                        zero(record.getDurationMinutes()), record.getCreateTime(), record.getRemark())).toList();
        List<LearnerProfileToolVO.Goal> goals = plans.stream().map(plan -> new LearnerProfileToolVO.Goal(
                plan.getTitle(), plan.getGoal(), plan.getStartDate(), plan.getEndDate(),
                parseTopics(plan.getFocusTopicsJson()), zero(plan.getCompletedTaskCount()),
                zero(plan.getTaskCount()))).toList();
        boolean hasData = !progress.isEmpty() || !quizzes.isEmpty() || !records.isEmpty() || !plans.isEmpty();
        return new LearnerProfileToolVO(hasData, goals, topics, weak, strengths, activities, quizTrend(quizzes));
    }

    private LearnerProfileToolVO.Topic topic(ToolDataRow row) {
        return new LearnerProfileToolVO.Topic(row.getTopic(), row.getMastery(), row.getQuizAccuracy(),
                zero(row.getStudyMinutes()), zero(row.getWrongCount()), zero(row.getQuizQuestionCount()));
    }

    private LearnerProfileToolVO.QuizTrend quizTrend(List<ToolDataRow> quizzes) {
        if (quizzes.isEmpty()) return null;
        List<ToolDataRow> recent = quizzes.subList(0, Math.min(5, quizzes.size()));
        List<ToolDataRow> previous = quizzes.size() > 5 ? quizzes.subList(5, quizzes.size()) : List.of();
        BigDecimal recentAverage = average(recent);
        BigDecimal previousAverage = previous.isEmpty() ? null : average(previous);
        String direction = recentAverage == null || previousAverage == null ? "INSUFFICIENT_HISTORY"
                : recentAverage.compareTo(previousAverage) > 0 ? "UP"
                : recentAverage.compareTo(previousAverage) < 0 ? "DOWN" : "STABLE";
        return new LearnerProfileToolVO.QuizTrend(direction, recentAverage, previousAverage,
                recent.size(), previous.size());
    }

    private BigDecimal average(List<ToolDataRow> rows) {
        List<BigDecimal> scores = rows.stream().map(ToolDataRow::getScore).filter(value -> value != null).toList();
        if (scores.isEmpty()) return null;
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private List<String> parseTopics(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private int zero(Integer value) { return value == null ? 0 : value; }

    public List<LearningProgressToolVO> learningProgress(Long userId, int limit) {
        return mapper.findLearningProgress(userId, limit).stream().map(row ->
                new LearningProgressToolVO(row.getTopic(), row.getMastery(), row.getQuizAccuracy(),
                        row.getStudyMinutes(), row.getWrongCount())).toList();
    }

    public List<WrongQuestionToolVO> wrongQuestions(Long userId, int limit) {
        return mapper.findWrongQuestions(userId, limit).stream().map(row ->
                new WrongQuestionToolVO(row.getId(), row.getQuestionId(), row.getTopic(), row.getContent(),
                        row.getWrongCount(), row.getMastered())).toList();
    }

    public List<WeakTopicToolVO> weakTopics(Long userId, int limit) {
        return mapper.findWeakTopics(userId, limit).stream().map(row ->
                new WeakTopicToolVO(row.getTopic(), row.getMastery(), row.getQuizAccuracy(), row.getWrongCount())).toList();
    }

    public List<RecentQuizToolVO> recentQuizResults(Long userId, int limit) {
        return mapper.findRecentQuizResults(userId, limit).stream().map(this::quiz).toList();
    }

    private RecentQuizToolVO quiz(ToolDataRow row) {
        return new RecentQuizToolVO(row.getId(), row.getTitle(), row.getTopic(), row.getDifficulty(), row.getStatus(),
                row.getScore(), row.getCorrectCount(), row.getWrongCount(), row.getCreateTime());
    }
}
