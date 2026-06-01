package com.yinbo.agent.auth.service;

import com.yinbo.agent.auth.dto.CurrentUserResponse;
import com.yinbo.agent.auth.dto.DeleteAccountRequest;
import com.yinbo.agent.auth.dto.LoginRequest;
import com.yinbo.agent.auth.dto.LoginResponse;
import com.yinbo.agent.auth.dto.LogoutResponse;
import com.yinbo.agent.auth.dto.RegisterRequest;
import com.yinbo.agent.auth.entity.AuthUser;
import jakarta.servlet.http.HttpServletRequest;

// 用户认证服务接口。
public interface AuthService {

    // 注册用户并建立登录态。
    LoginResponse register(RegisterRequest request, HttpServletRequest httpRequest);

    // 登录用户并建立登录态。
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);

    // 查询当前登录用户。
    CurrentUserResponse currentUser(HttpServletRequest httpRequest);

    // 退出当前登录态。
    LogoutResponse logout(HttpServletRequest httpRequest);

    // 注销当前账号。
    LogoutResponse deleteAccount(DeleteAccountRequest request, HttpServletRequest httpRequest);

    // 要求当前请求必须有可用登录用户。
    AuthUser requireActiveUser(HttpServletRequest httpRequest);
}
