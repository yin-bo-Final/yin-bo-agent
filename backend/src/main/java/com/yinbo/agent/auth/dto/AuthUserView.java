package com.yinbo.agent.auth.dto;

import java.time.LocalDateTime;

// 前端可见的用户信息。
public record AuthUserView(
        Long id,
        String username,
        String displayName,
        String role,
        Integer status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
