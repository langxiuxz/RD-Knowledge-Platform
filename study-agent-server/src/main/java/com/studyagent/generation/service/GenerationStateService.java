package com.studyagent.generation.service;

import com.studyagent.config.GenerationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GenerationStateService {
    private static final Logger log = LoggerFactory.getLogger(GenerationStateService.class);
    private static final String PREFIX = "generation:";
    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final Map<String, GenerationState> fallback = new ConcurrentHashMap<>();

    public GenerationStateService(StringRedisTemplate redisTemplate, GenerationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.ttl = properties.ttl() == null ? Duration.ofMinutes(10) : properties.ttl();
    }

    public void start(String traceId, Long userId, Long conversationId) {
        put(traceId, new GenerationState(userId, conversationId, "RUNNING"));
    }

    public GenerationState get(String traceId) {
        try {
            String value = redisTemplate.opsForValue().get(key(traceId));
            if (value != null) return decode(value);
        } catch (RuntimeException exception) {
            log.debug("Redis generation state unavailable, using in-memory fallback");
        }
        return fallback.get(traceId);
    }

    public void requestCancel(String traceId, Long userId, Long conversationId) {
        GenerationState current = get(traceId);
        if (current != null && (!current.userId().equals(userId) || !current.conversationId().equals(conversationId))) {
            return;
        }
        put(traceId, new GenerationState(userId, conversationId, "CANCEL_REQUESTED"));
    }

    public boolean isCancelRequested(String traceId) {
        GenerationState state = get(traceId);
        return state != null && "CANCEL_REQUESTED".equals(state.status());
    }

    public void markDone(String traceId) { updateStatus(traceId, "DONE"); }
    public void markFailed(String traceId) { updateStatus(traceId, "FAILED"); }

    private void updateStatus(String traceId, String status) {
        GenerationState current = get(traceId);
        if (current != null) put(traceId, new GenerationState(current.userId(), current.conversationId(), status));
    }

    private void put(String traceId, GenerationState state) {
        fallback.put(traceId, state);
        try {
            redisTemplate.opsForValue().set(key(traceId), encode(state), ttl);
        } catch (RuntimeException exception) {
            log.debug("Redis generation state write failed, state kept in-memory");
        }
    }

    private String key(String traceId) { return PREFIX + traceId; }
    private String encode(GenerationState state) { return state.userId() + "|" + state.conversationId() + "|" + state.status(); }
    private GenerationState decode(String value) {
        String[] parts = value.split("\\|", 3);
        return new GenerationState(Long.valueOf(parts[0]), Long.valueOf(parts[1]), parts[2]);
    }

    public record GenerationState(Long userId, Long conversationId, String status) {}
}
