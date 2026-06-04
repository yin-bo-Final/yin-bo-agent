package com.yinbo.ai.api.embedding;

import java.util.List;

// 向量化业务接口。
public interface EmbeddingService {

    // 对文本列表批量生成向量。
    List<float[]> embedBatch(List<String> texts, String modelId);

    // 返回默认向量维度。
    int dimension(String modelId);
}
