package com.studyagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.generation")
public record GenerationProperties(Duration ttl) {
}
