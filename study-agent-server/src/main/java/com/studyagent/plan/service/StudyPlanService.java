package com.studyagent.plan.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.plan.dto.GenerateStudyPlanRequest;
import com.studyagent.plan.dto.StudyPlanQuery;
import com.studyagent.plan.entity.StudyPlanEntity;
import com.studyagent.plan.mapper.StudyPlanMapper;
import com.studyagent.plan.mapper.StudyPlanTaskMapper;
import com.studyagent.plan.vo.StudyPlanVOs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class StudyPlanService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "COMPLETED", "ARCHIVED");
    private final StudyPlanMapper planMapper;
    private final StudyPlanTaskMapper taskMapper;
    private final StudyPlanGenerationPersistenceService persistenceService;
    private final StudyPlanGenerationWorker generationWorker;

    public StudyPlanService(StudyPlanMapper planMapper, StudyPlanTaskMapper taskMapper,
                            StudyPlanGenerationPersistenceService persistenceService,
                            StudyPlanGenerationWorker generationWorker) {
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.persistenceService = persistenceService;
        this.generationWorker = generationWorker;
    }

    public StudyPlanVOs.Generate generate(Long userId, GenerateStudyPlanRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endDate不能早于startDate");
        }
        StudyPlanEntity plan = persistenceService.create(userId, request);
        generationWorker.generate(userId, plan.getId(), request);
        return new StudyPlanVOs.Generate(plan.getId(), "PROCESSING");
    }

    public PageResult<StudyPlanVOs.Summary> list(Long userId, StudyPlanQuery query) {
        if (query.status() != null && !STATUSES.contains(query.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status不合法");
        }
        long total = planMapper.countByUserId(userId, query.status());
        List<StudyPlanVOs.Summary> rows = planMapper.findPageByUserId(userId, query.status(),
                        (query.page() - 1) * query.pageSize(), query.pageSize()).stream()
                .map(plan -> new StudyPlanVOs.Summary(plan.getId(), plan.getTitle(), plan.getStatus(), progress(plan)))
                .toList();
        return new PageResult<>(total, rows);
    }

    public StudyPlanVOs.Detail detail(Long userId, Long id) {
        StudyPlanEntity plan = requireOwned(userId, id);
        List<StudyPlanVOs.Task> tasks = "READY".equals(plan.getGenerationStatus())
                ? taskMapper.findByPlanIdAndUserId(id, userId).stream()
                    .map(task -> new StudyPlanVOs.Task(task.getId(), task.getTaskDate(), task.getTopic(),
                            task.getType(), task.getEstimatedMinutes(), task.getCompleted()))
                    .toList()
                : List.of();
        return new StudyPlanVOs.Detail(id, plan.getGenerationStatus(), plan.getStatus(),
                plan.getErrorMessage(), tasks);
    }

    @Transactional
    public void updateTask(Long userId, Long planId, Long taskId, boolean completed) {
        StudyPlanEntity plan = requireOwned(userId, planId);
        if (!"READY".equals(plan.getGenerationStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "学习计划尚未就绪");
        }
        if (taskMapper.updateCompleted(planId, taskId, userId, completed) == 0) throw notFound();
        String status = taskMapper.countIncomplete(planId, userId) == 0 ? "COMPLETED" : "ACTIVE";
        planMapper.updateStatus(planId, userId, status);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        if (planMapper.deleteByIdAndUserId(id, userId) == 0) throw notFound();
    }

    private StudyPlanEntity requireOwned(Long userId, Long id) {
        StudyPlanEntity entity = planMapper.findByIdAndUserId(id, userId);
        if (entity == null) throw notFound();
        return entity;
    }

    private int progress(StudyPlanEntity plan) {
        int total = plan.getTaskCount() == null ? 0 : plan.getTaskCount();
        int completed = plan.getCompletedTaskCount() == null ? 0 : plan.getCompletedTaskCount();
        return total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.FORBIDDEN, "学习计划不存在或无权访问");
    }
}
