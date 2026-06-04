package com.yinbo.ai.api.rerank;

import java.util.List;

// Rerank HTTP 响应。
public record RerankResponse(List<String> results) {

    // 规范化结果。
    public RerankResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
