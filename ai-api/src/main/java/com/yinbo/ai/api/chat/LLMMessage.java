package com.yinbo.ai.api.chat;

// 传给 LLM 的单条消息。
public record LLMMessage(
        String role,
        String content
) {
}
