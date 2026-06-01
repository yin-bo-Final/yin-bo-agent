package com.yinbo.agent.ingestion.model;

// 文档切块结果。
public record DocumentChunk(
        int index,
        String title,
        String content
) {
}
