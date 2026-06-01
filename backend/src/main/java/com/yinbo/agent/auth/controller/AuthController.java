package com.yinbo.agent.auth.controller;

import com.yinbo.agent.auth.dto.CurrentUserResponse;
import com.yinbo.agent.auth.dto.DeleteAccountRequest;
import com.yinbo.agent.auth.dto.LoginRequest;
import com.yinbo.agent.auth.dto.LoginResponse;
import com.yinbo.agent.auth.dto.LogoutResponse;
import com.yinbo.agent.auth.dto.RegisterRequest;
import com.yinbo.agent.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
// 用户认证接口。
public class AuthController {

    private final AuthService authService;

    // 注入认证服务。
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    // 注册新用户并建立登录会话。
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, httpRequest);
    }

    @PostMapping("/login")
    // 校验账号密码并建立登录会话。
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @GetMapping("/me")
    // 查询当前登录用户信息。
    public CurrentUserResponse currentUser(HttpServletRequest httpRequest) {
        return authService.currentUser(httpRequest);
    }

    @PostMapping("/logout")
    // 退出当前登录会话。
    public LogoutResponse logout(HttpServletRequest httpRequest) {
        return authService.logout(httpRequest);
    }

    @PostMapping("/cancel")
    // 注销当前账号。
    public LogoutResponse deleteAccount(@Valid @RequestBody DeleteAccountRequest request, HttpServletRequest httpRequest) {
        return authService.deleteAccount(request, httpRequest);
    }
}
