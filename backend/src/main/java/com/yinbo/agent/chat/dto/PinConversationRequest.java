package com.yinbo.agent.chat.dto;

public record PinConversationRequest(Boolean pinned) {

    public boolean pinnedEnabled() {
        return Boolean.TRUE.equals(pinned);
    }
}
