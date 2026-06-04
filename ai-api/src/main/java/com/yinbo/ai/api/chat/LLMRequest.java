package com.yinbo.ai.api.chat;

import java.util.List;

// LLM 对话请求。
public record LLMRequest(
        String modelId,
        boolean thinkMode,
        List<LLMMessage> messages
) {

    // 规范化消息列表。
    public LLMRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
