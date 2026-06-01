package com.yinbo.agent.auth;

import com.yinbo.agent.auth.service.AuthService;
import com.yinbo.agent.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
// 登录态拦截器。
public class LoginInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    // 注入认证服务。
    public LoginInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    // 拦截需要登录的请求并校验 Session。
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object loginUser = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(AuthConstants.LOGIN_USER_SESSION_KEY);
        if (loginUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或会话已过期，请重新登录");
        }
        authService.requireActiveUser(request);
        return true;
    }
}
