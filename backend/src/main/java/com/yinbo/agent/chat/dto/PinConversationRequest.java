package com.yinbo.agent.chat.dto;

// 会话置顶请求。
public record PinConversationRequest(Boolean pinned) {

    // 判断请求是否要求置顶。
    public boolean pinnedEnabled() {
        return Boolean.TRUE.equals(pinned);
    }
}
