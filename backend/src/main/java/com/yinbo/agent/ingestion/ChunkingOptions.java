package com.yinbo.agent.ingestion;

import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import org.springframework.http.HttpStatus;

public record ChunkingOptions(
        ChunkingStrategy strategy,
        int chunkSize,
        int chunkOverlap,
        int maxChunks
) {
    public static final int MAX_EMBEDDING_CHUNK_CHARS = 24_000;

    public static ChunkingOptions from(
            RagProperties ragProperties,
            String strategy,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer maxChunks
    ) {
        ChunkingStrategy resolvedStrategy = ChunkingStrategy.from(strategy);
        int resolvedChunkSize = chunkSize == null || chunkSize <= 0
                ? ragProperties.defaultChunkSize()
                : chunkSize;
        int resolvedChunkOverlap = chunkOverlap == null || chunkOverlap < 0
                ? ragProperties.defaultChunkOverlap()
                : chunkOverlap;
        int resolvedMaxChunks = maxChunks == null || maxChunks <= 0
                ? ragProperties.defaultMaxChunks()
                : maxChunks;

        if (resolvedChunkSize < 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "chunkSize 不能小于 100");
        }
        if (resolvedChunkOverlap >= resolvedChunkSize) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "chunkOverlap 必须小于 chunkSize");
        }
        if (resolvedMaxChunks > 2000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "maxChunks 暂时不能超过 2000");
        }
        return new ChunkingOptions(resolvedStrategy, resolvedChunkSize, resolvedChunkOverlap, resolvedMaxChunks);
    }

    public ChunkingOptions adaptForTextLength(int textLength) {
        if (strategy != ChunkingStrategy.AUTO || textLength <= 0) {
            return this;
        }

        int targetChunkSize = (int) Math.ceil(textLength / Math.max(1.0, maxChunks * 0.75));
        int resolvedChunkSize = Math.max(chunkSize, targetChunkSize);
        resolvedChunkSize = Math.min(MAX_EMBEDDING_CHUNK_CHARS, Math.max(1000, resolvedChunkSize));
        int resolvedOverlap = Math.min(chunkOverlap, Math.max(0, resolvedChunkSize / 5));
        return new ChunkingOptions(strategy, resolvedChunkSize, resolvedOverlap, maxChunks);
    }
}
