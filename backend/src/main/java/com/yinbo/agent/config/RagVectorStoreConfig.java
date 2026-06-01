package com.yinbo.agent.config;

import java.util.Locale;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIdType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
// RAG 向量存储配置。
public class RagVectorStoreConfig {

    private static final int MAX_INDEXED_VECTOR_DIMENSIONS = 2000;

    @Bean
    // 创建知识库使用的 PGVector 向量存储。
    public VectorStore knowledgeVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            RagProperties ragProperties
    ) {
        PgIndexType indexType = parseIndexType(ragProperties.vectorIndexType());
        validateIndexDimensions(indexType, ragProperties.embeddingDimensions());

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(ragProperties.vectorTableName())
                .idType(PgIdType.TEXT)
                .dimensions(ragProperties.embeddingDimensions())
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(indexType)
                .initializeSchema(false)
                .build();
    }

    // 解析 pgvector 索引类型。
    static PgIndexType parseIndexType(String value) {
        try {
            return PgIndexType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Unsupported pgvector index type: " + value + ". Use HNSW, IVFFLAT, or NONE.",
                    ex
            );
        }
    }

    // 校验索引类型和向量维度是否兼容。
    static void validateIndexDimensions(PgIndexType indexType, int dimensions) {
        if ((indexType == PgIndexType.HNSW || indexType == PgIndexType.IVFFLAT)
                && dimensions > MAX_INDEXED_VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "pgvector " + indexType + " index supports vector dimensions up to "
                            + MAX_INDEXED_VECTOR_DIMENSIONS
                            + ". Lower RAG_EMBEDDING_DIMENSIONS or set RAG_VECTOR_INDEX_TYPE=NONE."
            );
        }
    }
}
