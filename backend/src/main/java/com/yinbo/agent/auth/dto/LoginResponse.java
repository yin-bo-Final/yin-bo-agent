package com.yinbo.agent.auth.dto;

import java.time.Instant;

// 登录成功响应。
public record LoginResponse(
        String sessionId,
        Instant loginAt,
        AuthUserView user
) {
}
