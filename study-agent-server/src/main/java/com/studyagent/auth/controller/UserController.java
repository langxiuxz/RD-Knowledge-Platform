package com.studyagent.auth.controller;

import com.studyagent.auth.dto.ChangePasswordRequest;
import com.studyagent.auth.dto.UpdateProfileRequest;
import com.studyagent.auth.service.UserService;
import com.studyagent.auth.vo.UserProfileVO;
import com.studyagent.common.result.Result;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping
    public Result<UserProfileVO> currentUser() {
        return Result.success(userService.getCurrent(CurrentUser.requireId()));
    }

    @PutMapping
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(CurrentUser.requireId(), request);
        return Result.success();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(CurrentUser.requireId(), request);
        return Result.success();
    }
}
