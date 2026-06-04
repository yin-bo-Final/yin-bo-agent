package com.yinbo.ai.api.model;

// 前端可选择的模型展示项。
public record ModelOption(
        String id,
        String name,
        String provider,
        boolean enabled
) {
}
