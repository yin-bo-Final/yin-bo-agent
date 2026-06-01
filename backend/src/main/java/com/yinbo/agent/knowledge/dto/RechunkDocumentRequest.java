package com.yinbo.agent.knowledge.dto;

// 文档重新分块请求。
public record RechunkDocumentRequest(
        String strategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunks
) {
}
