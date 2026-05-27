package com.yinbo.agent.chat.dto;

import java.time.Instant;

public record ChatStreamEvent(
        String type,
        String conversationId,
        String modelId,
        String role,
        String content,
        Instant createdAt,
        Long responseDurationMs,
        Integer totalTokens,
        String error
) {

    public static ChatStreamEvent start(String conversationId, String modelId) {
        return new ChatStreamEvent("start", conversationId, modelId, "assistant", "", null, null, null, null);
    }

    public static ChatStreamEvent delta(String conversationId, String modelId, String content) {
        return new ChatStreamEvent("delta", conversationId, modelId, "assistant", content, null, null, null, null);
    }

    public static ChatStreamEvent done(String conversationId, String modelId, Instant createdAt, Long responseDurationMs, Integer totalTokens) {
        return new ChatStreamEvent("done", conversationId, modelId, "assistant", "", createdAt, responseDurationMs, totalTokens, null);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("error", null, null, "assistant", "", null, null, null, message);
    }
}
