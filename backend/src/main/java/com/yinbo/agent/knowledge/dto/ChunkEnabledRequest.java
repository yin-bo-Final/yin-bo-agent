package com.yinbo.agent.knowledge.dto;

// 分块启用状态更新请求。
public record ChunkEnabledRequest(Boolean enabled) {

    // 获取默认后的启用状态。
    public boolean enabledValue() {
        return Boolean.TRUE.equals(enabled);
    }
}
