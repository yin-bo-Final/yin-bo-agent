package com.yinbo.agent.knowledge.dto;

import java.time.Instant;

public record KnowledgeChunkResponse(
        String chunkId,
        Integer chunkIndex,
        String title,
        String content,
        Boolean enabled,
        Integer tokenCount,
        Integer charCount,
        Instant updatedAt
) {
}
