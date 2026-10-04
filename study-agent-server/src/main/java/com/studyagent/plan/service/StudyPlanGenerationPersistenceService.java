package com.studyagent.plan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.plan.dto.GenerateStudyPlanRequest;
import com.studyagent.plan.entity.StudyPlanEntity;
import com.studyagent.plan.entity.StudyPlanTaskEntity;
import com.studyagent.plan.mapper.StudyPlanMapper;
import com.studyagent.plan.mapper.StudyPlanTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Service
public class StudyPlanGenerationPersistenceService {
    private static final Set<String> TASK_TYPES = Set.of("READ", "QUIZ", "REVIEW", "CHAT");
    private final StudyPlanMapper planMapper;
    private final StudyPlanTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    public StudyPlanGenerationPersistenceService(StudyPlanMapper planMapper, StudyPlanTaskMapper taskMapper,
                                                  ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StudyPlanEntity create(Long userId, GenerateStudyPlanRequest request) {
        StudyPlanEntity entity = new StudyPlanEntity();
        entity.setUserId(userId);
        entity.setTitle(truncate(request.goal(), 128));
        entity.setGoal(request.goal());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setDailyMinutes(request.dailyMinutes());
        entity.setFocusTopicsJson(writeJson(request.focusTopics() == null ? List.of() : request.focusTopics()));
        entity.setGenerationStatus("PROCESSING");
        entity.setStatus("ACTIVE");
        planMapper.insert(entity);
        return entity;
    }

    @Transactional
    public void complete(Long userId, Long planId, AiClient.PlanGenerateResponse response) {
        StudyPlanEntity plan = planMapper.findByIdAndUserId(planId, userId);
        if (plan == null || !"PROCESSING".equals(plan.getGenerationStatus())) return;
        if (response == null || response.tasks() == null || response.tasks().isEmpty()) {
            throw new BusinessException(ErrorCode.STRUCTURED_OUTPUT_INVALID, "学习计划任务不能为空");
        }
        int order = 0;
        for (AiClient.GeneratedPlanTask source : response.tasks()) {
            LocalDate date = parseDate(source.date());
            if (date.isBefore(plan.getStartDate()) || date.isAfter(plan.getEndDate())) {
                throw new BusinessException(ErrorCode.STRUCTURED_OUTPUT_INVALID, "学习计划任务日期越界");
            }
            if (source.topic() == null || source.topic().isBlank() || source.topic().length() > 128
                    || !TASK_TYPES.contains(source.type()) || source.estimatedMinutes() == null
                    || source.estimatedMinutes() < 0 || source.estimatedMinutes() > plan.getDailyMinutes()) {
                throw new BusinessException(ErrorCode.STRUCTURED_OUTPUT_INVALID, "学习计划任务字段不合法");
            }
            StudyPlanTaskEntity task = new StudyPlanTaskEntity();
            task.setPlanId(planId);
            task.setUserId(userId);
            task.setTaskDate(date);
            task.setTopic(source.topic());
            task.setType(source.type());
            task.setEstimatedMinutes(source.estimatedMinutes());
            task.setCompleted(false);
            task.setSortOrder(order++);
            taskMapper.insert(task);
        }
        String title = response.title() == null || response.title().isBlank()
                ? plan.getTitle() : truncate(response.title(), 128);
        if (planMapper.markReady(planId, userId, title) == 0) {
            throw new BusinessException(ErrorCode.STUDY_PLAN_GENERATION_FAILED, "学习计划状态已变化");
        }
    }

    @Transactional
    public void fail(Long userId, Long planId, String message) {
        planMapper.markFailed(planId, userId, truncate(
                message == null || message.isBlank() ? ErrorCode.STUDY_PLAN_GENERATION_FAILED.message() : message,
                1000));
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.STRUCTURED_OUTPUT_INVALID, "学习计划任务日期不合法");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
