package com.yinbo.agent.chat.dto;

import java.time.Instant;

public record ConversationMessageResponse(
        String role,
        String content,
        String modelId,
        Instant createdAt,
        Long responseDurationMs,
        Integer totalTokens
) {
}
