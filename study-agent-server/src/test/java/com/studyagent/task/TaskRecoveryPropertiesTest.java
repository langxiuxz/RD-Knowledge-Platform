package com.studyagent.task;

import com.studyagent.config.TaskRecoveryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRecoveryPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void bind_whenNoOverrides_shouldUseSafeDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(TaskRecoveryProperties.class)).isEqualTo(
                    new TaskRecoveryProperties(true, Duration.ofMinutes(15), Duration.ofMinutes(5), Duration.ofMinutes(5), 100));
        });
    }

    @Test
    void bind_whenEnvironmentOverridesProvided_shouldUseTypedDurations() {
        runner.withPropertyValues(
                "app.task-recovery.enabled=${TASK_RECOVERY_ENABLED:true}",
                "app.task-recovery.document-timeout=${TASK_RECOVERY_DOCUMENT_TIMEOUT:15m}",
                "app.task-recovery.generation-timeout=${TASK_RECOVERY_GENERATION_TIMEOUT:5m}",
                "app.task-recovery.report-timeout=${TASK_RECOVERY_REPORT_TIMEOUT:5m}",
                "app.task-recovery.batch-size=${TASK_RECOVERY_BATCH_SIZE:100}")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("systemEnvironment", Map.of(
                        "TASK_RECOVERY_ENABLED", "false",
                        "TASK_RECOVERY_DOCUMENT_TIMEOUT", "20m",
                        "TASK_RECOVERY_GENERATION_TIMEOUT", "2m",
                        "TASK_RECOVERY_REPORT_TIMEOUT", "3m",
                        "TASK_RECOVERY_BATCH_SIZE", "10"))))
                .run(context -> assertThat(context.getBean(TaskRecoveryProperties.class)).isEqualTo(
                        new TaskRecoveryProperties(false, Duration.ofMinutes(20), Duration.ofMinutes(2), Duration.ofMinutes(3), 10)));
    }

    @Test
    void bind_whenTimeoutInvalid_shouldRejectStartup() {
        runner.withPropertyValues("app.task-recovery.document-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bind_whenBatchUnbounded_shouldRejectStartup() {
        runner.withPropertyValues("app.task-recovery.batch-size=1001")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TaskRecoveryProperties.class)
    static class Config {}
}
