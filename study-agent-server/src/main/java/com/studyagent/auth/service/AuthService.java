package com.studyagent.auth.service;

import com.studyagent.auth.dto.LoginRequest;
import com.studyagent.auth.dto.RefreshTokenRequest;
import com.studyagent.auth.dto.RegisterRequest;
import com.studyagent.auth.entity.RefreshTokenEntity;
import com.studyagent.auth.entity.UserEntity;
import com.studyagent.auth.mapper.RefreshTokenMapper;
import com.studyagent.auth.mapper.UserMapper;
import com.studyagent.auth.vo.LoginVO;
import com.studyagent.auth.vo.RefreshTokenVO;
import com.studyagent.auth.vo.RegisterVO;
import com.studyagent.auth.vo.UserSummaryVO;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.config.AppProperties;
import com.studyagent.security.CurrentUser;
import com.studyagent.security.JwtService;
import com.studyagent.security.PasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserMapper userMapper, RefreshTokenMapper refreshTokenMapper,
                       PasswordService passwordService, JwtService jwtService, AppProperties properties) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public RegisterVO register(RegisterRequest request) {
        if (userMapper.findByUsername(request.username()) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return new RegisterVO(user.getId(), user.getUsername());
    }

    @Transactional
    public LoginVO login(LoginRequest request) {
        UserEntity user = userMapper.findByUsername(request.username());
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || !passwordService.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = newRefreshToken();
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(user.getId());
        entity.setSessionId(sessionId);
        entity.setTokenHash(hashToken(refreshToken));
        entity.setExpiresAt(LocalDateTime.now().plus(properties.jwt().refreshTokenExpiration()));
        refreshTokenMapper.insert(entity);
        String accessToken = jwtService.createAccessToken(user.getId(), user.getUsername(), sessionId);
        return new LoginVO(new UserSummaryVO(user.getId(), user.getUsername(), user.getNickname()),
                accessToken, properties.jwt().accessTokenExpiration().toSeconds(), refreshToken,
                properties.jwt().refreshTokenExpiration().toSeconds());
    }

    @Transactional
    public void logout() {
        Long userId = CurrentUser.requireId();
        CurrentUser.sessionId().ifPresent(sessionId -> refreshTokenMapper.revokeByUserAndSession(userId, sessionId));
    }

    @Transactional
    public RefreshTokenVO refresh(RefreshTokenRequest request) {
        RefreshTokenEntity token = refreshTokenMapper.findByTokenHash(hashToken(request.refreshToken()));
        if (token == null || token.getRevokedAt() != null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        UserEntity user = userMapper.findById(token.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        refreshTokenMapper.markUsed(token.getId());
        return new RefreshTokenVO(jwtService.createAccessToken(user.getId(), user.getUsername(), token.getSessionId()),
                properties.jwt().accessTokenExpiration().toSeconds());
    }

    static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
