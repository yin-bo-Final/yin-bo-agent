package com.yinbo.agent.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("chat_pipeline_config")
// 会话流水线可运行配置。
public class ChatPipelineConfig {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("terminology_enabled")
    private Boolean terminologyEnabled;

    @TableField("llm_rewrite_enabled")
    private Boolean llmRewriteEnabled;

    @TableField("rule_split_enabled")
    private Boolean ruleSplitEnabled;

    @TableField("fallback_policy")
    private String fallbackPolicy;

    @TableField("rewrite_timeout_ms")
    private Integer rewriteTimeoutMs;

    @TableField("rewrite_context_turns")
    private Integer rewriteContextTurns;

    @TableField("updated_by")
    private Long updatedBy;

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

    public Boolean getTerminologyEnabled() {
        return terminologyEnabled;
    }

    public void setTerminologyEnabled(Boolean terminologyEnabled) {
        this.terminologyEnabled = terminologyEnabled;
    }

    public Boolean getLlmRewriteEnabled() {
        return llmRewriteEnabled;
    }

    public void setLlmRewriteEnabled(Boolean llmRewriteEnabled) {
        this.llmRewriteEnabled = llmRewriteEnabled;
    }

    public Boolean getRuleSplitEnabled() {
        return ruleSplitEnabled;
    }

    public void setRuleSplitEnabled(Boolean ruleSplitEnabled) {
        this.ruleSplitEnabled = ruleSplitEnabled;
    }

    public String getFallbackPolicy() {
        return fallbackPolicy;
    }

    public void setFallbackPolicy(String fallbackPolicy) {
        this.fallbackPolicy = fallbackPolicy;
    }

    public Integer getRewriteTimeoutMs() {
        return rewriteTimeoutMs;
    }

    public void setRewriteTimeoutMs(Integer rewriteTimeoutMs) {
        this.rewriteTimeoutMs = rewriteTimeoutMs;
    }

    public Integer getRewriteContextTurns() {
        return rewriteContextTurns;
    }

    public void setRewriteContextTurns(Integer rewriteContextTurns) {
        this.rewriteContextTurns = rewriteContextTurns;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
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
