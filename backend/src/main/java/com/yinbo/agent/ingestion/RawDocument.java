package com.yinbo.agent.ingestion;

public record RawDocument(
        DocumentSourceType sourceType,
        String sourceUrl,
        String fileName,
        String contentType,
        long sizeBytes,
        byte[] bytes
) {
}
