package com.yinbo.agent.ingestion.dto;

import java.time.Instant;

// 后台文档入库任务响应。
public record IngestionTaskResponse(
        String taskId,
        String action,
        String status,
        String documentId,
        String fileName,
        String documentStatus,
        String strategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunks,
        Integer retryCount,
        Integer maxRetries,
        String lastError,
        String sourceRequestId,
        String mqMessageId,
        Instant lastStartedAt,
        Instant lastFailedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
