package com.yinbo.agent.ingestion.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// 直接操作 PGVector 表，确保向量数据可加入当前 PostgreSQL 事务。
public class PgVectorRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String vectorTableName;

    // 注入 JDBC、JSON 序列化和 RAG 配置。
    public PgVectorRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, RagProperties ragProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.vectorTableName = safeTableName(ragProperties.vectorTableName());
    }

    // 批量插入向量行。
    public void insertAll(List<PgVectorRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + vectorTableName + " (id, content, metadata, embedding) VALUES (?, ?, ?::json, ?::vector)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                PgVectorRow row = rows.get(index);
                ps.setString(1, row.id());
                ps.setString(2, row.content());
                ps.setString(3, toJson(row.metadata()));
                ps.setString(4, toPgVectorLiteral(row.embedding()));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    // 根据向量 ID 批量删除。
    public void deleteByIds(Collection<String> ids) {
        List<String> vectorIds = ids == null ? List.of() : ids.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        if (vectorIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", vectorIds.stream().map(value -> "?").toList());
        jdbcTemplate.update(
                "DELETE FROM " + vectorTableName + " WHERE id IN (" + placeholders + ")",
                vectorIds.toArray()
        );
    }

    // 将 metadata 序列化为 JSON。
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "向量 metadata 序列化失败");
        }
    }

    // 将 float[] 转为 pgvector 字面量。
    private String toPgVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Embedding 结果为空");
        }
        StringBuilder builder = new StringBuilder(embedding.length * 10);
        builder.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(embedding[i]));
        }
        builder.append(']');
        return builder.toString();
    }

    // 限制表名只允许 schema.table 或普通标识符，避免配置值进入 SQL 注入面。
    private String safeTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")) {
            throw new IllegalArgumentException("Invalid vector table name: " + tableName);
        }
        return tableName;
    }
}
