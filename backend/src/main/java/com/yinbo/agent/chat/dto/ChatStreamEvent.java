package com.yinbo.agent.chat.dto;

import java.time.Instant;

public record ChatStreamEvent(
        String type,
        String conversationId,
        String modelId,
        String role,
        String content,
        Instant createdAt,
        String error
) {

    public static ChatStreamEvent start(String conversationId, String modelId) {
        return new ChatStreamEvent("start", conversationId, modelId, "assistant", "", null, null);
    }

    public static ChatStreamEvent delta(String conversationId, String modelId, String content) {
        return new ChatStreamEvent("delta", conversationId, modelId, "assistant", content, null, null);
    }

    public static ChatStreamEvent done(String conversationId, String modelId, Instant createdAt) {
        return new ChatStreamEvent("done", conversationId, modelId, "assistant", "", createdAt, null);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("error", null, null, "assistant", "", null, message);
    }
}
