package com.yinbo.agent.auth.dto;

import java.time.LocalDateTime;

public record AuthUserView(
        Long id,
        String username,
        String displayName,
        Integer status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
