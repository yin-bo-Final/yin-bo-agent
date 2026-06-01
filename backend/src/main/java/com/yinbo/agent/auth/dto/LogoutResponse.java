package com.yinbo.agent.auth.dto;

import java.time.Instant;

// 退出登录或注销账号响应。
public record LogoutResponse(
        String message,
        Instant loggedOutAt
) {
}
