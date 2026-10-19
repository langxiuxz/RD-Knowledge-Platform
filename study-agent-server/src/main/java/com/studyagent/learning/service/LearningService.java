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
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

@Service
public class LearningService {
    private static final Set<String> TYPES = Set.of("READ", "QUIZ", "REVIEW", "CHAT", "FOCUS");
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
        boolean focus = "FOCUS".equals(request.type());
        if (focus && (request.clientRecordId() == null || request.completedAt() == null
                || request.durationMinutes() == null || request.durationMinutes() < 1 || request.sourceId() != null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "专注记录需要编号、完成时间和至少1分钟时长，sourceId须为空");
        }
        if (!focus && (request.clientRecordId() != null || request.completedAt() != null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "编号和完成时间仅用于专注记录");
        }
        if (request.completedAt() != null && request.completedAt().isAfter(OffsetDateTime.now().plusMinutes(1))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "完成时间不能在未来");
        }
        LearningRecordEntity entity = new LearningRecordEntity();
        entity.setUserId(userId);
        entity.setType(request.type());
        entity.setTopic(request.topic());
        entity.setDurationMinutes(request.durationMinutes() == null ? 0 : request.durationMinutes());
        entity.setSourceId(request.sourceId());
        entity.setRemark(request.remark());
        entity.setClientRecordId(request.clientRecordId());
        if (focus) entity.setCreateTime(request.completedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().withNano(0));
        try {
            recordMapper.insert(entity);
        } catch (DuplicateKeyException duplicate) {
            if (request.clientRecordId() == null) throw duplicate;
            // A locking read sees the committed winner even under MySQL REPEATABLE READ.
            LearningRecordEntity existing = recordMapper.findByClientRecordId(userId, request.clientRecordId());
            if (existing == null) throw duplicate;
            if (!Objects.equals(existing.getType(), entity.getType())
                    || !Objects.equals(existing.getTopic(), entity.getTopic())
                    || !Objects.equals(existing.getDurationMinutes(), entity.getDurationMinutes())
                    || !Objects.equals(existing.getSourceId(), entity.getSourceId())
                    || !Objects.equals(existing.getRemark(), entity.getRemark())
                    || !Objects.equals(existing.getCreateTime(), entity.getCreateTime())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "同一专注编号不能用于不同记录");
            }
            return new LearningVOs.Id(existing.getId());
        }
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
        if (type == null || !TYPES.contains(type)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "type不合法");
    }
}
