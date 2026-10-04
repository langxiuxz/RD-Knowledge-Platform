package com.studyagent.auth;

import com.studyagent.auth.dto.ChangePasswordRequest;
import com.studyagent.auth.entity.UserEntity;
import com.studyagent.auth.mapper.RefreshTokenMapper;
import com.studyagent.auth.mapper.UserMapper;
import com.studyagent.auth.service.UserService;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.security.PasswordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserMapper userMapper;
    @Mock RefreshTokenMapper refreshTokenMapper;

    private final PasswordService passwordService = new PasswordService();

    @Test
    void getCurrent_shouldReturnOnlyCurrentUserProfile() {
        UserEntity user = activeUser(7L, "alice", "Alice");
        user.setEmail("alice@example.com");
        when(userMapper.findById(7L)).thenReturn(user);

        var result = new UserService(userMapper, refreshTokenMapper, passwordService).getCurrent(7L);

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.email()).isEqualTo("alice@example.com");
        verify(userMapper).findById(7L);
    }

    @Test
    void changePassword_withWrongOldPassword_shouldRejectAndKeepSessions() {
        UserEntity user = activeUser(7L, "alice", "Alice");
        user.setPasswordHash(passwordService.hash("correct-password"));
        when(userMapper.findById(7L)).thenReturn(user);
        var service = new UserService(userMapper, refreshTokenMapper, passwordService);

        assertThatThrownBy(() -> service.changePassword(7L,
                new ChangePasswordRequest("wrong-password", "new-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(userMapper, never()).updatePassword(any(), any());
        verify(refreshTokenMapper, never()).revokeAllByUserId(7L);
    }

    @Test
    void changePassword_withCorrectOldPassword_shouldUpdateHashAndRevokeSessions() {
        UserEntity user = activeUser(7L, "alice", "Alice");
        user.setPasswordHash(passwordService.hash("correct-password"));
        when(userMapper.findById(7L)).thenReturn(user);
        var service = new UserService(userMapper, refreshTokenMapper, passwordService);

        service.changePassword(7L, new ChangePasswordRequest("correct-password", "new-password"));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePassword(org.mockito.ArgumentMatchers.eq(7L), hash.capture());
        assertThat(passwordService.matches("new-password", hash.getValue())).isTrue();
        verify(refreshTokenMapper).revokeAllByUserId(7L);
    }

    @Test
    void getCurrent_whenUserDisabled_shouldReject() {
        UserEntity user = activeUser(7L, "alice", "Alice");
        user.setStatus("DISABLED");
        when(userMapper.findById(7L)).thenReturn(user);

        assertThatThrownBy(() -> new UserService(userMapper, refreshTokenMapper, passwordService).getCurrent(7L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private UserEntity activeUser(Long id, String username, String nickname) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setStatus("ACTIVE");
        return user;
    }
}
