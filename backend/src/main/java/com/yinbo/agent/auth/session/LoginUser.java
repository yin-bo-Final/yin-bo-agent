package com.yinbo.agent.auth.session;

import java.io.Serializable;
import java.time.Instant;

public record LoginUser(
        Long id,
        String username,
        String displayName,
        Instant loginAt
) implements Serializable {
}
