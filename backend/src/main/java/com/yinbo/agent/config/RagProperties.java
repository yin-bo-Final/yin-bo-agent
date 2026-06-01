package com.yinbo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.rag")
// RAG 入库和向量检索配置。
public record RagProperties(
        String embeddingModel,
        String rerankerModel,
        Integer embeddingDimensions,
        String vectorIndexType,
        String vectorTableName,
        Integer defaultChunkSize,
        Integer defaultChunkOverlap,
        Integer defaultMaxChunks,
        Integer minChunkSize,
        Long maxSourceBytes,
        String ingestionTopic,
        String ingestionConsumerGroup
) {

    // 给 RAG 配置补默认值并修正非法组合。
    public RagProperties {
        embeddingModel = blankToDefault(embeddingModel, "Qwen/Qwen3-Embedding-8B");
        rerankerModel = blankToDefault(rerankerModel, "Qwen/Qwen3-Reranker-8B");
        embeddingDimensions = positiveOrDefault(embeddingDimensions, 1024);
        vectorIndexType = blankToDefault(vectorIndexType, "HNSW").toUpperCase();
        vectorTableName = blankToDefault(vectorTableName, "knowledge_chunk_vector");
        defaultChunkSize = positiveOrDefault(defaultChunkSize, 1000);
        defaultChunkOverlap = nonNegativeOrDefault(defaultChunkOverlap, 150);
        defaultMaxChunks = positiveOrDefault(defaultMaxChunks, 200);
        minChunkSize = positiveOrDefault(minChunkSize, 80);
        maxSourceBytes = maxSourceBytes == null || maxSourceBytes <= 0 ? 50L * 1024L * 1024L : maxSourceBytes;
        ingestionTopic = blankToDefault(ingestionTopic, "rag-ingestion-task");
        ingestionConsumerGroup = blankToDefault(ingestionConsumerGroup, "yinbo-agent-ingestion-consumer");
        if (defaultChunkOverlap >= defaultChunkSize) {
            defaultChunkOverlap = Math.max(0, defaultChunkSize / 5);
        }
    }

    // 空字符串回退到默认值。
    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    // 非正整数回退到默认值。
    private static Integer positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    // 负整数回退到默认值。
    private static Integer nonNegativeOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }
}
