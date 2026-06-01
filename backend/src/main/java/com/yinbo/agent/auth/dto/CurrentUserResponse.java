package com.yinbo.agent.auth.dto;

import java.time.Instant;

// 当前登录用户响应。
public record CurrentUserResponse(
        String sessionId,
        Instant loginAt,
        AuthUserView user
) {
}
