package com.yinbo.agent.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

// AI 对话请求。
public record ChatRequest(
        String conversationId,
        @NotBlank String modelId,
        @NotEmpty List<@Valid ChatMessage> messages,
        Boolean thinkMode
) {

    // 判断是否启用 Think 模式。
    public boolean thinkModeEnabled() {
        return Boolean.TRUE.equals(thinkMode);
    }
}
