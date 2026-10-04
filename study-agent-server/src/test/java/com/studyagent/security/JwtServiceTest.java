package com.studyagent.security;

import com.studyagent.config.AppProperties;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    @Test
    void accessTokenRoundTripPreservesPrincipalClaims() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(
                        "phase0-test-secret-with-at-least-32-characters",
                        Duration.ofMinutes(5),
                        Duration.ofDays(1)),
                new AppProperties.Ai("http://localhost:8000", Duration.ofSeconds(1), Duration.ofSeconds(5)),
                new AppProperties.Storage("./data/files", "http://localhost:8080/files"),
                new AppProperties.Internal("phase0-internal-token"));
        JwtService jwtService = new JwtService(properties);
        jwtService.initialize();

        String token = jwtService.createAccessToken(42L, "alice");
        var claims = jwtService.parse(token);

        assertTrue(jwtService.isAccessToken(claims));
        assertEquals(42L, jwtService.userId(claims));
        assertEquals("alice", claims.getSubject());
        assertTrue(claims.get("sid", String.class) != null);
    }

    @Test
    void expiredToken_shouldBeRejected() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("phase0-test-secret-with-at-least-32-characters", Duration.ofSeconds(-1), Duration.ofDays(1)),
                new AppProperties.Ai("http://localhost:8000", Duration.ofSeconds(1), Duration.ofSeconds(5)),
                new AppProperties.Storage("./data/files", "http://localhost:8080/files"),
                new AppProperties.Internal("phase0-internal-token"));
        JwtService jwtService = new JwtService(properties);
        jwtService.initialize();
        String token = jwtService.createAccessToken(42L, "alice", "session");

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> jwtService.parse(token));
    }

    @Test
    void authenticationFilter_withIllegalToken_shouldReturnUnauthorized() throws Exception {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("phase0-test-secret-with-at-least-32-characters", Duration.ofMinutes(5), Duration.ofDays(1)),
                new AppProperties.Ai("http://localhost:8000", Duration.ofSeconds(1), Duration.ofSeconds(5)),
                new AppProperties.Storage("./data/files", "http://localhost:8080/files"),
                new AppProperties.Internal("phase0-internal-token"));
        JwtService jwtService = new JwtService(properties);
        jwtService.initialize();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }
}
