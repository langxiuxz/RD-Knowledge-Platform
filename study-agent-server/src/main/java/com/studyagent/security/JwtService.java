package com.studyagent.security;

import com.studyagent.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final AppProperties properties;
    private SecretKey signingKey;

    public JwtService(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        String configured = properties.jwt().secret();
        signingKey = configured == null || configured.isBlank()
                ? Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256)
                : Keys.hmacShaKeyFor(configured.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username) {
        return createAccessToken(userId, username, UUID.randomUUID().toString());
    }

    public String createAccessToken(Long userId, String username, String sessionId) {
        return createToken(userId, username, sessionId, "access", properties.jwt().accessTokenExpiration());
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("type", String.class));
    }

    public Long userId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    public String sessionId(Claims claims) {
        return claims.get("sid", String.class);
    }

    private String createToken(Long userId, String username, String sessionId, String type, Duration expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("sid", sessionId)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }
}
