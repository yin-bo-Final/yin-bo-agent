package com.yinbo.agent.chat.dto;

import java.time.Instant;

// 非流式 AI 对话响应。
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
