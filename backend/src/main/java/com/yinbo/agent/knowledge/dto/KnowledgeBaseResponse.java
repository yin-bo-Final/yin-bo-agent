package com.yinbo.agent.knowledge.dto;

import java.time.Instant;

// 知识库响应。
public record KnowledgeBaseResponse(
        String knowledgeBaseId,
        String name,
        String embeddingModel,
        String collectionName,
        String status,
        long documentCount,
        long chunkCount,
        Instant createdAt,
        Instant updatedAt
) {
}
