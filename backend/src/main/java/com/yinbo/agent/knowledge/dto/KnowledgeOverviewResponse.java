package com.yinbo.agent.knowledge.dto;

// 知识库概览统计响应。
public record KnowledgeOverviewResponse(
        long knowledgeBaseCount,
        long totalDocumentCount,
        long knowledgeBaseWithDocumentsCount,
        String embeddingModel
) {
}
