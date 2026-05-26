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
}
