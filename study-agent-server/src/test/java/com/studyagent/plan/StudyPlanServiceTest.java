package com.studyagent.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.plan.dto.GenerateStudyPlanRequest;
import com.studyagent.plan.entity.StudyPlanEntity;
import com.studyagent.plan.mapper.StudyPlanMapper;
import com.studyagent.plan.mapper.StudyPlanTaskMapper;
import com.studyagent.plan.service.StudyPlanGenerationPersistenceService;
import com.studyagent.plan.service.StudyPlanGenerationWorker;
import com.studyagent.plan.service.StudyPlanService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyPlanServiceTest {
    @Mock StudyPlanMapper planMapper;
    @Mock StudyPlanTaskMapper taskMapper;
    @Mock StudyPlanGenerationPersistenceService persistenceService;
    @Mock StudyPlanGenerationWorker generationWorker;

    @Test
    void updateTask_whenLastTaskCompleted_shouldCompletePlan() {
        StudyPlanService service = service();
        when(planMapper.findByIdAndUserId(10L, 1L)).thenReturn(readyPlan());
        when(taskMapper.updateCompleted(10L, 20L, 1L, true)).thenReturn(1);
        when(taskMapper.countIncomplete(10L, 1L)).thenReturn(0);

        service.updateTask(1L, 10L, 20L, true);

        verify(planMapper).updateStatus(10L, 1L, "COMPLETED");
    }

    @Test
    void detail_whenPlanBelongsToAnotherUser_shouldReject() {
        StudyPlanService service = service();
        when(planMapper.findByIdAndUserId(10L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(taskMapper);
    }

    @Test
    void generate_whenExecutorRejects_shouldPersistFailureForPolling() {
        var request = new GenerateStudyPlanRequest("learn", LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 20), 30, List.of("Java"));
        var plan = readyPlan();
        when(persistenceService.create(1L, request)).thenReturn(plan);
        doThrow(new TaskRejectedException("full")).when(generationWorker).generate(1L, 10L, request);

        assertThat(service().generate(1L, request).planId()).isEqualTo(10L);

        verify(persistenceService).fail(eq(1L), eq(10L), contains("队列已满"));
    }

    @Test
    void complete_whenTimeoutAlreadyWon_shouldIgnoreLateResponse() {
        var plan = readyPlan();
        plan.setGenerationStatus("FAILED");
        when(planMapper.findByIdAndUserId(10L, 1L)).thenReturn(plan);
        var persistence = new StudyPlanGenerationPersistenceService(planMapper, taskMapper, new ObjectMapper());

        persistence.complete(1L, 10L, null);

        verifyNoInteractions(taskMapper);
        verify(planMapper, never()).markReady(anyLong(), anyLong(), anyString());
    }

    private StudyPlanService service() {
        return new StudyPlanService(planMapper, taskMapper, persistenceService, generationWorker);
    }

    private StudyPlanEntity readyPlan() {
        StudyPlanEntity plan = new StudyPlanEntity();
        plan.setId(10L);
        plan.setUserId(1L);
        plan.setGenerationStatus("READY");
        plan.setStatus("ACTIVE");
        return plan;
    }
}
