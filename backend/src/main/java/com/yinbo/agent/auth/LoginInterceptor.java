package com.yinbo.agent.auth;

import com.yinbo.agent.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public LoginInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
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
