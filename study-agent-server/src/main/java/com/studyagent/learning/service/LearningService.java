package com.studyagent.learning.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.learning.dto.CreateLearningRecordRequest;
import com.studyagent.learning.dto.LearningRecordQuery;
import com.studyagent.learning.entity.LearningRecordEntity;
import com.studyagent.learning.mapper.LearningRecordMapper;
import com.studyagent.learning.mapper.TopicProgressMapper;
import com.studyagent.learning.vo.LearningVOs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class LearningService {
    private static final Set<String> TYPES = Set.of("READ", "QUIZ", "REVIEW", "CHAT");
    private static final Set<String> RANGES = Set.of("7d", "30d", "all");
    private final LearningRecordMapper recordMapper;
    private final TopicProgressMapper progressMapper;

    public LearningService(LearningRecordMapper recordMapper, TopicProgressMapper progressMapper) {
        this.recordMapper = recordMapper;
        this.progressMapper = progressMapper;
    }

    @Transactional
    public LearningVOs.Id create(Long userId, CreateLearningRecordRequest request) {
        validateType(request.type());
        LearningRecordEntity entity = new LearningRecordEntity();
        entity.setUserId(userId);
        entity.setType(request.type());
        entity.setTopic(request.topic());
        entity.setDurationMinutes(request.durationMinutes() == null ? 0 : request.durationMinutes());
        entity.setSourceId(request.sourceId());
        entity.setRemark(request.remark());
        recordMapper.insert(entity);
        progressMapper.addStudyMinutes(userId, request.topic(), entity.getDurationMinutes());
        return new LearningVOs.Id(entity.getId());
    }

    public PageResult<LearningVOs.Record> list(Long userId, LearningRecordQuery query) {
        if (query.type() != null) validateType(query.type());
        if (query.begin() != null && query.end() != null && query.end().isBefore(query.begin())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "end不能早于begin");
        }
        LocalDateTime begin = query.begin() == null ? null : query.begin().atStartOfDay();
        LocalDateTime end = query.end() == null ? null : query.end().plusDays(1).atStartOfDay();
        long total = recordMapper.countByUserId(userId, begin, end, query.type(), query.topic());
        var rows = recordMapper.findPageByUserId(userId, begin, end, query.type(), query.topic(),
                        (query.page() - 1) * query.pageSize(), query.pageSize()).stream()
                .map(row -> new LearningVOs.Record(row.getId(), row.getType(), row.getTopic(), row.getDurationMinutes()))
                .toList();
        return new PageResult<>(total, rows);
    }

    public LearningVOs.Progress progress(Long userId, String range) {
        String actual = range == null || range.isBlank() ? "all" : range;
        if (!RANGES.contains(actual)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "range不合法");
        var topics = progressMapper.findByUserId(userId).stream()
                .map(row -> new LearningVOs.Topic(row.getTopic(), row.getMastery(), row.getQuizAccuracy(), row.getStudyMinutes()))
                .toList();
        return new LearningVOs.Progress(topics);
    }

    private void validateType(String type) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "type不合法");
    }
}
