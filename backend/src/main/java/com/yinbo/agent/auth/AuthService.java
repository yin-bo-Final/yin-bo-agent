package com.yinbo.agent.auth;

import com.yinbo.agent.auth.dto.CurrentUserResponse;
import com.yinbo.agent.auth.dto.DeleteAccountRequest;
import com.yinbo.agent.auth.dto.LoginRequest;
import com.yinbo.agent.auth.dto.LoginResponse;
import com.yinbo.agent.auth.dto.LogoutResponse;
import com.yinbo.agent.auth.dto.RegisterRequest;
import com.yinbo.agent.auth.entity.AuthUser;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    LoginResponse register(RegisterRequest request, HttpServletRequest httpRequest);

    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);

    CurrentUserResponse currentUser(HttpServletRequest httpRequest);

    LogoutResponse logout(HttpServletRequest httpRequest);

    LogoutResponse deleteAccount(DeleteAccountRequest request, HttpServletRequest httpRequest);

    AuthUser requireActiveUser(HttpServletRequest httpRequest);
}
