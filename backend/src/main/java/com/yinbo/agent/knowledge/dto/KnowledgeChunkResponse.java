package com.yinbo.agent.knowledge.dto;

import java.time.Instant;

// 知识分块响应。
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
