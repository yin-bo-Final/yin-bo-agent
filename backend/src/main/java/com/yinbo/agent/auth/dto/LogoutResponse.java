package com.yinbo.agent.auth.dto;

import java.time.Instant;

public record LogoutResponse(
        String message,
        Instant loggedOutAt
) {
}
