package com.studyagent.plan;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.plan.entity.StudyPlanEntity;
import com.studyagent.plan.mapper.StudyPlanMapper;
import com.studyagent.plan.mapper.StudyPlanTaskMapper;
import com.studyagent.plan.service.StudyPlanGenerationPersistenceService;
import com.studyagent.plan.service.StudyPlanGenerationWorker;
import com.studyagent.plan.service.StudyPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
