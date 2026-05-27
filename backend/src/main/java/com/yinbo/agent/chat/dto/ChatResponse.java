package com.yinbo.agent.chat.dto;

import java.time.Instant;

public record ChatResponse(
        String conversationId,
        String modelId,
        String role,
        String content,
        Instant createdAt,
        Long responseDurationMs,
        Integer totalTokens
) {
}
