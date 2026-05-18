package com.yinbo.agent.auth.dto;

import java.time.Instant;

public record CurrentUserResponse(
        String sessionId,
        Instant loginAt,
        AuthUserView user
) {
}
