package com.yinbo.ai.api.rerank;

import java.util.List;

// Rerank HTTP 请求。
public record RerankRequest(
        String query,
        List<String> candidates,
        int topN
) {

    // 规范化候选文本。
    public RerankRequest {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
