package com.yinbo.agent.common;

import java.time.Instant;

// 统一错误响应结构。
public record ApiErrorResponse(
        int status,
        String message,
        Instant timestamp
) {
}
