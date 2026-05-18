package com.yinbo.agent.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessage(
        @NotBlank String role,
        @NotBlank String content
) {
}
