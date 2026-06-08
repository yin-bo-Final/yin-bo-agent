package com.yinbo.agent.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("conversation_memory_summary")
// 会话记忆摘要实体，记录已压缩消息的水位线。
public class ConversationMemorySummary {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("user_id")
    private Long userId;

    @TableField("summary_content")
    private String summaryContent;

    @TableField("covered_start_message_id")
    private Long coveredStartMessageId;

    @TableField("covered_end_message_id")
    private Long coveredEndMessageId;

    @TableField("source_message_count")
    private Integer sourceMessageCount;

    @TableField("summary_tokens")
    private Integer summaryTokens;

    @TableField("compression_model_id")
    private String compressionModelId;

    @TableField("compression_version")
    private String compressionVersion;

    @TableField("trigger_type")
    private String triggerType;

    private String status;

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

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSummaryContent() {
        return summaryContent;
    }

    public void setSummaryContent(String summaryContent) {
        this.summaryContent = summaryContent;
    }

    public Long getCoveredStartMessageId() {
        return coveredStartMessageId;
    }

    public void setCoveredStartMessageId(Long coveredStartMessageId) {
        this.coveredStartMessageId = coveredStartMessageId;
    }

    public Long getCoveredEndMessageId() {
        return coveredEndMessageId;
    }

    public void setCoveredEndMessageId(Long coveredEndMessageId) {
        this.coveredEndMessageId = coveredEndMessageId;
    }

    public Integer getSourceMessageCount() {
        return sourceMessageCount;
    }

    public void setSourceMessageCount(Integer sourceMessageCount) {
        this.sourceMessageCount = sourceMessageCount;
    }

    public Integer getSummaryTokens() {
        return summaryTokens;
    }

    public void setSummaryTokens(Integer summaryTokens) {
        this.summaryTokens = summaryTokens;
    }

    public String getCompressionModelId() {
        return compressionModelId;
    }

    public void setCompressionModelId(String compressionModelId) {
        this.compressionModelId = compressionModelId;
    }

    public String getCompressionVersion() {
        return compressionVersion;
    }

    public void setCompressionVersion(String compressionVersion) {
        this.compressionVersion = compressionVersion;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
