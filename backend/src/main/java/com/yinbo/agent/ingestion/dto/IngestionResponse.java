package com.yinbo.agent.ingestion.dto;

import java.time.Instant;

public record IngestionResponse(
        String documentId,
        String sourceType,
        String sourceUrl,
        String fileName,
        String contentType,
        String status,
        String parser,
        Integer textCharCount,
        Integer chunkCount,
        String chunkStrategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunks,
        Instant createdAt,
        String errorMessage
) {
}
