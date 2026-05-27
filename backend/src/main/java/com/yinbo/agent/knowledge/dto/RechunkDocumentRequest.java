package com.yinbo.agent.knowledge.dto;

public record RechunkDocumentRequest(
        String strategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunks
) {
}
