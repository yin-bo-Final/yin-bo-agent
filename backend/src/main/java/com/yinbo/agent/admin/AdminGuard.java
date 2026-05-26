package com.yinbo.agent.admin;

import com.yinbo.agent.auth.AuthService;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AdminGuard {

    private static final String ROLE_ADMIN = "ADMIN";

    private final AuthService authService;

    public AdminGuard(AuthService authService) {
        this.authService = authService;
    }

    public AuthUser requireAdmin(HttpServletRequest request) {
        AuthUser authUser = authService.requireActiveUser(request);
        if (!ROLE_ADMIN.equals(authUser.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有管理员可以访问管理后台");
        }
        return authUser;
    }
}
