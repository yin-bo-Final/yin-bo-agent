package com.yinbo.agent.ingestion.vector;

import java.util.Map;

// 待写入 PGVector 表的向量行。
public record PgVectorRow(
        String id,
        String content,
        Map<String, Object> metadata,
        float[] embedding
) {
}
