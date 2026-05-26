package com.yinbo.agent.knowledge.dto;

public record KnowledgeOverviewResponse(
        long knowledgeBaseCount,
        long totalDocumentCount,
        long knowledgeBaseWithDocumentsCount,
        String embeddingModel
) {
}
