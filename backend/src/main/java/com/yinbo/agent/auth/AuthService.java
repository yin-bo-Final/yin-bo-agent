package com.yinbo.agent.auth;

import com.yinbo.agent.auth.dto.CurrentUserResponse;
import com.yinbo.agent.auth.dto.LoginRequest;
import com.yinbo.agent.auth.dto.LoginResponse;
import com.yinbo.agent.auth.dto.LogoutResponse;
import com.yinbo.agent.auth.entity.AuthUser;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);

    CurrentUserResponse currentUser(HttpServletRequest httpRequest);

    LogoutResponse logout(HttpServletRequest httpRequest);

    AuthUser requireActiveUser(HttpServletRequest httpRequest);
}
