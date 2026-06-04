package com.yinbo.agent.ingestion.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ingestion_task")
// 文档入库任务实体。
public class IngestionTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("document_id")
    private Long documentId;

    @TableField("document_no")
    private String documentNo;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    private String action;

    private String status;

    private String strategy;

    @TableField("chunk_size")
    private Integer chunkSize;

    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    @TableField("max_chunks")
    private Integer maxChunks;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("max_retries")
    private Integer maxRetries;

    @TableField("last_error")
    private String lastError;

    @TableField("source_request_id")
    private String sourceRequestId;

    @TableField("mq_message_id")
    private String mqMessageId;

    @TableField("last_started_at")
    private LocalDateTime lastStartedAt;

    @TableField("last_failed_at")
    private LocalDateTime lastFailedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

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

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
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

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getSourceRequestId() {
        return sourceRequestId;
    }

    public void setSourceRequestId(String sourceRequestId) {
        this.sourceRequestId = sourceRequestId;
    }

    public String getMqMessageId() {
        return mqMessageId;
    }

    public void setMqMessageId(String mqMessageId) {
        this.mqMessageId = mqMessageId;
    }

    public LocalDateTime getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(LocalDateTime lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public LocalDateTime getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(LocalDateTime lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
