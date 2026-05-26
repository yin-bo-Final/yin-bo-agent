package com.yinbo.agent.ingestion;

public record DocumentChunk(
        int index,
        String title,
        String content
) {
}
