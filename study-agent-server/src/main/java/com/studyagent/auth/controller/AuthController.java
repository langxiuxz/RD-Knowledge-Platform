package com.studyagent.auth.controller;

import com.studyagent.auth.dto.LoginRequest;
import com.studyagent.auth.dto.RefreshTokenRequest;
import com.studyagent.auth.dto.RegisterRequest;
import com.studyagent.auth.service.AuthService;
import com.studyagent.auth.vo.LoginVO;
import com.studyagent.auth.vo.RefreshTokenVO;
import com.studyagent.auth.vo.RegisterVO;
import com.studyagent.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public ResponseEntity<Result<RegisterVO>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(authService.register(request)));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<RefreshTokenVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request));
    }
}
