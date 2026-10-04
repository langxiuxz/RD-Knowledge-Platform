package com.studyagent.auth.service;

import com.studyagent.auth.dto.ChangePasswordRequest;
import com.studyagent.auth.dto.UpdateProfileRequest;
import com.studyagent.auth.entity.UserEntity;
import com.studyagent.auth.mapper.RefreshTokenMapper;
import com.studyagent.auth.mapper.UserMapper;
import com.studyagent.auth.vo.UserProfileVO;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.security.PasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordService passwordService;

    public UserService(UserMapper userMapper, RefreshTokenMapper refreshTokenMapper, PasswordService passwordService) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordService = passwordService;
    }

    public UserProfileVO getCurrent(Long userId) {
        return toProfile(requireUser(userId));
    }

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        UserEntity user = requireUser(userId);
        if (request.nickname() != null) user.setNickname(request.nickname());
        if (request.avatar() != null) user.setAvatar(request.avatar());
        if (request.email() != null) user.setEmail(request.email());
        userMapper.updateProfile(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        UserEntity user = requireUser(userId);
        if (!passwordService.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "原密码错误");
        }
        userMapper.updatePassword(userId, passwordService.hash(request.newPassword()));
        refreshTokenMapper.revokeAllByUserId(userId);
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private UserProfileVO toProfile(UserEntity user) {
        return new UserProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getEmail(), user.getCreateTime());
    }
}
