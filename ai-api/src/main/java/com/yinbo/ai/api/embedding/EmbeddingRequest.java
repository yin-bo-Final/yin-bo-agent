package com.yinbo.ai.api.embedding;

import java.util.List;

// Embedding HTTP 请求。
public record EmbeddingRequest(
        String modelId,
        List<String> texts
) {

    // 规范化文本列表。
    public EmbeddingRequest {
        texts = texts == null ? List.of() : List.copyOf(texts);
    }
}
