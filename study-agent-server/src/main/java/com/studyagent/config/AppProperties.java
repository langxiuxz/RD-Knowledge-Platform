package com.studyagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Ai ai, Storage storage, Internal internal) {

    public record Jwt(String secret, Duration accessTokenExpiration, Duration refreshTokenExpiration) {}

    public record Ai(String baseUrl, Duration connectTimeout, Duration readTimeout) {}

    public record Storage(String localRoot, String publicBaseUrl) {}

    public record Internal(String token) {}
}
