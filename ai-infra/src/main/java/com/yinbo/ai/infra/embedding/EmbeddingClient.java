package com.yinbo.ai.infra.embedding;

import com.yinbo.ai.infra.model.ModelTarget;
import java.util.List;

// Embedding 供应商客户端。
public interface EmbeddingClient {

    // 返回供应商标识。
    String provider();

    // 批量生成向量。
    List<float[]> embedBatch(List<String> texts, ModelTarget target);
}
