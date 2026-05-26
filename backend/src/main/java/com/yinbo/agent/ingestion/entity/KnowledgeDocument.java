package com.yinbo.agent.ingestion.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("document_no")
    private String documentNo;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("user_id")
    private Long userId;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("file_name")
    private String fileName;

    @TableField("content_type")
    private String contentType;

    private String parser;

    @TableField("original_size_bytes")
    private Long originalSizeBytes;

    @TableField("text_char_count")
    private Integer textCharCount;

    @TableField("chunk_count")
    private Integer chunkCount;

    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("chunk_strategy")
    private String chunkStrategy;

    @TableField("chunk_size")
    private Integer chunkSize;

    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    @TableField("max_chunks")
    private Integer maxChunks;

    @TableField("text_extracted_at")
    private LocalDateTime textExtractedAt;

    @TableField("parse_duration_ms")
    private Long parseDurationMs;

    @TableField("chunk_duration_ms")
    private Long chunkDurationMs;

    @TableField("embedding_duration_ms")
    private Long embeddingDurationMs;

    @TableField("other_duration_ms")
    private Long otherDurationMs;

    @TableField("total_duration_ms")
    private Long totalDurationMs;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getParser() {
        return parser;
    }

    public void setParser(String parser) {
        this.parser = parser;
    }

    public Long getOriginalSizeBytes() {
        return originalSizeBytes;
    }

    public void setOriginalSizeBytes(Long originalSizeBytes) {
        this.originalSizeBytes = originalSizeBytes;
    }

    public Integer getTextCharCount() {
        return textCharCount;
    }

    public void setTextCharCount(Integer textCharCount) {
        this.textCharCount = textCharCount;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getChunkStrategy() {
        return chunkStrategy;
    }

    public void setChunkStrategy(String chunkStrategy) {
        this.chunkStrategy = chunkStrategy;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(Integer chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public Integer getMaxChunks() {
        return maxChunks;
    }

    public void setMaxChunks(Integer maxChunks) {
        this.maxChunks = maxChunks;
    }

    public LocalDateTime getTextExtractedAt() {
        return textExtractedAt;
    }

    public void setTextExtractedAt(LocalDateTime textExtractedAt) {
        this.textExtractedAt = textExtractedAt;
    }

    public Long getParseDurationMs() {
        return parseDurationMs;
    }

    public void setParseDurationMs(Long parseDurationMs) {
        this.parseDurationMs = parseDurationMs;
    }

    public Long getChunkDurationMs() {
        return chunkDurationMs;
    }

    public void setChunkDurationMs(Long chunkDurationMs) {
        this.chunkDurationMs = chunkDurationMs;
    }

    public Long getEmbeddingDurationMs() {
        return embeddingDurationMs;
    }

    public void setEmbeddingDurationMs(Long embeddingDurationMs) {
        this.embeddingDurationMs = embeddingDurationMs;
    }

    public Long getOtherDurationMs() {
        return otherDurationMs;
    }

    public void setOtherDurationMs(Long otherDurationMs) {
        this.otherDurationMs = otherDurationMs;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(Long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
