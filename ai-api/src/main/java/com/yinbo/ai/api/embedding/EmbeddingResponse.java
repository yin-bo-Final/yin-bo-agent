package com.yinbo.ai.api.embedding;

import java.util.List;

// Embedding HTTP 响应。
public record EmbeddingResponse(
        List<List<Float>> embeddings,
        Integer dimension
) {

    // 规范化向量列表。
    public EmbeddingResponse {
        embeddings = embeddings == null ? List.of() : List.copyOf(embeddings);
    }
}
