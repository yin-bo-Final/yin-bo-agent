package com.yinbo.agent.knowledge.dto;

public record ChunkEnabledRequest(Boolean enabled) {

    public boolean enabledValue() {
        return Boolean.TRUE.equals(enabled);
    }
}
