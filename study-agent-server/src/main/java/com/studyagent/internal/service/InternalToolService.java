package com.studyagent.internal.service;

import com.studyagent.internal.entity.ToolDataRow;
import com.studyagent.internal.mapper.ToolDataMapper;
import com.studyagent.internal.vo.LearningProgressToolVO;
import com.studyagent.internal.vo.RecentQuizToolVO;
import com.studyagent.internal.vo.WeakTopicToolVO;
import com.studyagent.internal.vo.WrongQuestionToolVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InternalToolService {
    private final ToolDataMapper mapper;

    public InternalToolService(ToolDataMapper mapper) { this.mapper = mapper; }

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
