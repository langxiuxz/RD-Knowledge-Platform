package com.studyagent.task;

import com.studyagent.task.service.TaskRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.task-recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaskRecoveryScheduler {
    private final TaskRecoveryService recovery;

    public TaskRecoveryScheduler(TaskRecoveryService recovery) {
        this.recovery = recovery;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        recovery.recover();
    }

    @Scheduled(fixedDelayString = "${app.task-recovery.scan-interval:30s}",
            initialDelayString = "${app.task-recovery.scan-interval:30s}")
    public void recover() {
        recovery.recover();
    }
}
