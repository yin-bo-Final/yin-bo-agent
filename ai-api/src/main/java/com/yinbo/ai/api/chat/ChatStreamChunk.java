package com.yinbo.ai.api.chat;

// ai-infra 到 backend 的流式传输片段。
public record ChatStreamChunk(
        String type,
        String delta,
        LLMResponse response,
        String message
) {

    // 创建增量输出片段。
    public static ChatStreamChunk delta(String delta) {
        return new ChatStreamChunk("delta", delta, null, null);
    }

    // 创建完成片段。
    public static ChatStreamChunk done(LLMResponse response) {
        return new ChatStreamChunk("done", null, response, null);
    }

    // 创建错误片段。
    public static ChatStreamChunk error(String message) {
        return new ChatStreamChunk("error", null, null, message);
    }
}
