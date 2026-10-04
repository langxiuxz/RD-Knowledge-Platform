package com.studyagent.health.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.health.dto.HealthVO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final JdbcTemplate jdbcTemplate;
    private final AiClient aiClient;

    public HealthService(JdbcTemplate jdbcTemplate, AiClient aiClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiClient = aiClient;
    }

    public HealthVO check() {
        boolean databaseUp;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            databaseUp = true;
        } catch (DataAccessException | IllegalStateException exception) {
            databaseUp = false;
        }
        boolean aiUp = aiClient.isHealthy();
        String status = databaseUp && aiUp ? "UP" : "DOWN";
        return new HealthVO(status, aiUp ? "UP" : "DOWN", databaseUp ? "UP" : "DOWN");
    }
}
