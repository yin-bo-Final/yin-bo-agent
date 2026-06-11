package com.yinbo.agent.chat.dto;

import java.time.Instant;

// 会话消息响应。
public record ConversationMessageResponse(
        String role,
        String content,
        String modelId,
        Instant createdAt,
        Long responseDurationMs,
        Integer totalTokens,
        Long messageId,
        ChatAssistantTraceResponse assistantTrace
) {
}
