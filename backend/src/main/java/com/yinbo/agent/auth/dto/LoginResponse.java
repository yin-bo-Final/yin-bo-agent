package com.yinbo.agent.auth.dto;

import java.time.Instant;

public record LoginResponse(
        String sessionId,
        Instant loginAt,
        AuthUserView user
) {
}
