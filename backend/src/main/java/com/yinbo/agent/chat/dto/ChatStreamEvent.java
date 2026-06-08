package com.yinbo.agent.chat.dto;

import java.time.Instant;

// SSE 流式对话事件。
public record ChatStreamEvent(
        String type,
        String conversationId,
        String modelId,
        String role,
        String content,
        Instant createdAt,
        Long responseDurationMs,
        Integer totalTokens,
        String error,
        ConversationMemorySummaryResponse memorySummary
) {

    // 创建流式开始事件。
    public static ChatStreamEvent start(String conversationId, String modelId) {
        return start(conversationId, modelId, null);
    }

    // 创建带会话记忆摘要的流式开始事件。
    public static ChatStreamEvent start(
            String conversationId,
            String modelId,
            ConversationMemorySummaryResponse memorySummary
    ) {
        return new ChatStreamEvent("start", conversationId, modelId, "assistant", "", null, null, null, null, memorySummary);
    }

    // 创建流式增量内容事件。
    public static ChatStreamEvent delta(String conversationId, String modelId, String content) {
        return new ChatStreamEvent("delta", conversationId, modelId, "assistant", content, null, null, null, null, null);
    }

    // 创建流式完成事件。
    public static ChatStreamEvent done(String conversationId, String modelId, Instant createdAt, Long responseDurationMs, Integer totalTokens) {
        return new ChatStreamEvent("done", conversationId, modelId, "assistant", "", createdAt, responseDurationMs, totalTokens, null, null);
    }

    // 创建流式错误事件。
    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("error", null, null, "assistant", "", null, null, null, message, null);
    }
}
