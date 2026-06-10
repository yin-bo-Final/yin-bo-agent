package com.yinbo.agent.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("chat_intent_rule")
// 会话意图规则实体。
public class ChatIntentRule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("rule_code")
    private String ruleCode;

    private String name;

    private String description;

    @TableField("target_node_code")
    private String targetNodeCode;

    @TableField("rule_type")
    private String ruleType;

    @TableField("include_keywords_json")
    private String includeKeywordsJson;

    @TableField("include_match_mode")
    private String includeMatchMode;

    @TableField("require_keywords_json")
    private String requireKeywordsJson;

    @TableField("require_match_mode")
    private String requireMatchMode;

    @TableField("exclude_keywords_json")
    private String excludeKeywordsJson;

    private BigDecimal score;

    private Boolean enabled;

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

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTargetNodeCode() {
        return targetNodeCode;
    }

    public void setTargetNodeCode(String targetNodeCode) {
        this.targetNodeCode = targetNodeCode;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getIncludeKeywordsJson() {
        return includeKeywordsJson;
    }

    public void setIncludeKeywordsJson(String includeKeywordsJson) {
        this.includeKeywordsJson = includeKeywordsJson;
    }

    public String getIncludeMatchMode() {
        return includeMatchMode;
    }

    public void setIncludeMatchMode(String includeMatchMode) {
        this.includeMatchMode = includeMatchMode;
    }

    public String getRequireKeywordsJson() {
        return requireKeywordsJson;
    }

    public void setRequireKeywordsJson(String requireKeywordsJson) {
        this.requireKeywordsJson = requireKeywordsJson;
    }

    public String getRequireMatchMode() {
        return requireMatchMode;
    }

    public void setRequireMatchMode(String requireMatchMode) {
        this.requireMatchMode = requireMatchMode;
    }

    public String getExcludeKeywordsJson() {
        return excludeKeywordsJson;
    }

    public void setExcludeKeywordsJson(String excludeKeywordsJson) {
        this.excludeKeywordsJson = excludeKeywordsJson;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
