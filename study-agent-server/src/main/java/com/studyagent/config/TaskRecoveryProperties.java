package com.studyagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.task-recovery")
public record TaskRecoveryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("15m") Duration documentTimeout,
        @DefaultValue("5m") Duration generationTimeout,
        @DefaultValue("100") int batchSize) {
    public TaskRecoveryProperties {
        if (documentTimeout == null || documentTimeout.getSeconds() < 1
                || generationTimeout == null || generationTimeout.getSeconds() < 1) {
            throw new IllegalArgumentException("Task recovery timeouts must be at least one second");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Task recovery batch size must be between 1 and 1000");
        }
    }
}
