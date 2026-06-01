package com.yinbo.agent.auth.session;

import java.io.Serializable;
import java.time.Instant;

// 写入 Session 的登录用户快照。
public record LoginUser(
        Long id,
        String username,
        String displayName,
        Instant loginAt
) implements Serializable {
}
