package com.studyagent.auth;

import com.studyagent.auth.dto.LoginRequest;
import com.studyagent.auth.dto.RefreshTokenRequest;
import com.studyagent.auth.dto.RegisterRequest;
import com.studyagent.auth.entity.RefreshTokenEntity;
import com.studyagent.auth.entity.UserEntity;
import com.studyagent.auth.mapper.RefreshTokenMapper;
import com.studyagent.auth.mapper.UserMapper;
import com.studyagent.auth.service.AuthService;
import com.studyagent.auth.vo.LoginVO;
import com.studyagent.auth.vo.RefreshTokenVO;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.config.AppProperties;
import com.studyagent.security.JwtService;
import com.studyagent.security.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserMapper userMapper;
    @Mock RefreshTokenMapper refreshTokenMapper;
    private AuthService authService;
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("auth-test-secret-with-at-least-32-characters", Duration.ofMinutes(5), Duration.ofDays(7)),
                new AppProperties.Ai("http://localhost", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                new AppProperties.Storage("./data", "http://localhost/files"),
                new AppProperties.Internal("internal-test"));
        jwtService = new JwtService(properties);
        ReflectionTestUtils.invokeMethod(jwtService, "initialize");
        authService = new AuthService(userMapper, refreshTokenMapper, new PasswordService(), jwtService, properties);
    }

    @Test
    void register_whenUsernameAlreadyExists_shouldReject() {
        when(userMapper.findByUsername("alice")).thenReturn(new UserEntity());

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "password123", "Alice", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.USERNAME_EXISTS);
    }

    @Test
    void login_withWrongPassword_shouldReject() {
        UserEntity user = user(7L, "alice", "ACTIVE");
        user.setPasswordHash(new PasswordService().hash("correct-password"));
        when(userMapper.findByUsername("alice")).thenReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_withValidCredentials_shouldPersistOnlyRefreshHashAndIssueSid() {
        UserEntity user = user(7L, "alice", "ACTIVE");
        user.setNickname("Alice");
        user.setPasswordHash(new PasswordService().hash("correct-password"));
        when(userMapper.findByUsername("alice")).thenReturn(user);

        LoginVO result = authService.login(new LoginRequest("alice", "correct-password"));

        assertThat(result.user().id()).isEqualTo(7L);
        assertThat(result.token()).isNotBlank();
        var claims = jwtService.parse(result.token());
        assertThat(claims.get("sid", String.class)).isNotBlank();
        ArgumentCaptor<com.studyagent.auth.entity.RefreshTokenEntity> captor = ArgumentCaptor.forClass(com.studyagent.auth.entity.RefreshTokenEntity.class);
        verify(refreshTokenMapper).insert(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(result.refreshToken());
        assertThat(captor.getValue().getTokenHash()).hasSize(64);
    }

    @Test
    void refresh_withValidToken_shouldIssueAccessTokenAndMarkUsage() {
        RefreshTokenEntity stored = new RefreshTokenEntity();
        stored.setId(11L);
        stored.setUserId(7L);
        stored.setSessionId("session-7");
        stored.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        UserEntity user = user(7L, "alice", "ACTIVE");
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(stored);
        when(userMapper.findById(7L)).thenReturn(user);

        RefreshTokenVO result = authService.refresh(new RefreshTokenRequest("rt_plain"));

        assertThat(result.token()).isNotBlank();
        assertThat(jwtService.sessionId(jwtService.parse(result.token()))).isEqualTo("session-7");
        verify(refreshTokenMapper).markUsed(11L);
    }

    @Test
    void refresh_withExpiredToken_shouldRejectWithoutLoadingUser() {
        RefreshTokenEntity stored = new RefreshTokenEntity();
        stored.setId(11L);
        stored.setUserId(7L);
        stored.setExpiresAt(java.time.LocalDateTime.now().minusSeconds(1));
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(stored);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("rt_plain")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void logout_shouldRevokeCurrentSessionOnly() {
        var principal = new com.studyagent.security.UserPrincipal(7L, "alice", "session-7");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        authService.logout();

        verify(refreshTokenMapper).revokeByUserAndSession(7L, "session-7");
    }

    private UserEntity user(Long id, String username, String status) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(status);
        return user;
    }
}
