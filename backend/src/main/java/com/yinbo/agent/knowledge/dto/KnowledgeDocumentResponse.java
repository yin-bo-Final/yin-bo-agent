package com.yinbo.agent.knowledge.dto;

import java.time.Instant;

public record KnowledgeDocumentResponse(
        String documentId,
        String fileName,
        String sourceType,
        String sourceUrl,
        String contentType,
        Long originalSizeBytes,
        String status,
        Integer textCharCount,
        Integer chunkCount,
        String chunkStrategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunks,
        Instant textExtractedAt,
        Long parseDurationMs,
        Long chunkDurationMs,
        Long embeddingDurationMs,
        Long otherDurationMs,
        Long totalDurationMs,
        Instant createdAt,
        Instant updatedAt,
        String errorMessage
) {
}
