package com.yinbo.agent.chat.dto;

import jakarta.validation.constraints.NotBlank;

// 前端传入的单条对话消息。
public record ChatMessage(
        @NotBlank String role,
        @NotBlank String content
) {
}
