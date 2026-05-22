package com.yinbo.agent.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ChatRequest(
        String conversationId,
        @NotBlank String modelId,
        @NotEmpty List<@Valid ChatMessage> messages,
        Boolean thinkMode
) {

    public boolean thinkModeEnabled() {
        return Boolean.TRUE.equals(thinkMode);
    }
}
