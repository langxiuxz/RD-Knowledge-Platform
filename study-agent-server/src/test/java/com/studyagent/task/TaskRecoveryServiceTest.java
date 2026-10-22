package com.studyagent.task;

import com.studyagent.config.TaskRecoveryProperties;
import com.studyagent.insight.mapper.CapabilityReportMapper;
import com.studyagent.document.mapper.DocumentVersionMapper;
import com.studyagent.plan.mapper.StudyPlanMapper;
import com.studyagent.quiz.mapper.QuizMapper;
import com.studyagent.task.service.TaskRecoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskRecoveryServiceTest {
    @Mock DocumentVersionMapper documents;
    @Mock QuizMapper quizzes;
    @Mock StudyPlanMapper plans;
    @Mock CapabilityReportMapper reports;

    @Test
    void recover_whenEnabled_shouldUseBoundedUpdatesForAllThreeModules() {
        recovery(true).recover();
        verify(documents).failStaleTasks(900, 100, TaskRecoveryService.DOCUMENT_TIMEOUT);
        verify(quizzes).failStaleTasks(300, 100, TaskRecoveryService.GENERATION_TIMEOUT);
        verify(plans).failStaleTasks(300, 100, TaskRecoveryService.GENERATION_TIMEOUT);
        verify(reports).failStale(eq(300L), eq(100), anyString());
    }

    @Test
    void recover_whenOneModuleFails_shouldContinueOtherModules() {
        when(documents.failStaleTasks(anyLong(), anyInt(), anyString())).thenThrow(new IllegalStateException());
        recovery(true).recover();
        verify(quizzes).failStaleTasks(300, 100, TaskRecoveryService.GENERATION_TIMEOUT);
        verify(plans).failStaleTasks(300, 100, TaskRecoveryService.GENERATION_TIMEOUT);
    }

    @Test
    void recover_whenDisabled_shouldNotAccessDatabase() {
        recovery(false).recover();
        verifyNoInteractions(documents, quizzes, plans, reports);
    }

    @Test
    void scheduler_whenApplicationBecomesReady_shouldRunRecovery() {
        TaskRecoveryService service = mock(TaskRecoveryService.class);
        TaskRecoveryScheduler scheduler = new TaskRecoveryScheduler(service);
        scheduler.onReady();
        scheduler.recover();
        verify(service, times(2)).recover();
    }

    private TaskRecoveryService recovery(boolean enabled) {
        return new TaskRecoveryService(documents, quizzes, plans, reports,
                new TaskRecoveryProperties(enabled, Duration.ofMinutes(15), Duration.ofMinutes(5), Duration.ofMinutes(5), 100));
    }
}
