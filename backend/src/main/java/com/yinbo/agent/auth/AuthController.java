package com.yinbo.agent.auth;

import com.yinbo.agent.auth.dto.CurrentUserResponse;
import com.yinbo.agent.auth.dto.DeleteAccountRequest;
import com.yinbo.agent.auth.dto.LoginRequest;
import com.yinbo.agent.auth.dto.LoginResponse;
import com.yinbo.agent.auth.dto.LogoutResponse;
import com.yinbo.agent.auth.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, httpRequest);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(HttpServletRequest httpRequest) {
        return authService.currentUser(httpRequest);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(HttpServletRequest httpRequest) {
        return authService.logout(httpRequest);
    }

    @PostMapping("/cancel")
    public LogoutResponse deleteAccount(@Valid @RequestBody DeleteAccountRequest request, HttpServletRequest httpRequest) {
        return authService.deleteAccount(request, httpRequest);
    }
}
