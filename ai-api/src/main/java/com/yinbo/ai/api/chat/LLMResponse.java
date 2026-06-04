package com.yinbo.ai.api.chat;

// LLM 对话响应。
public record LLMResponse(
        String modelId,
        String content,
        TokenUsage usage
) {

    // Token 用量。
    public record TokenUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
    }
}
