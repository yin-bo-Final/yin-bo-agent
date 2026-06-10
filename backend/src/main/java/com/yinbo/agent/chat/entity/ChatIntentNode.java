package com.yinbo.agent.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("chat_intent_node")
// 会话意图树节点实体。
public class ChatIntentNode {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("node_code")
    private String nodeCode;

    @TableField("parent_code")
    private String parentCode;

    private String name;

    private String description;

    private String level;

    private String kind;

    @TableField("examples_json")
    private String examplesJson;

    @TableField("knowledge_base_no")
    private String knowledgeBaseNo;

    @TableField("collection_name")
    private String collectionName;

    @TableField("mcp_tool_id")
    private String mcpToolId;

    @TableField("prompt_snippet")
    private String promptSnippet;

    @TableField("prompt_template")
    private String promptTemplate;

    @TableField("param_prompt_template")
    private String paramPromptTemplate;

    @TableField("top_k")
    private Integer topK;

    @TableField("min_score")
    private BigDecimal minScore;

    @TableField("sort_order")
    private Integer sortOrder;

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

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
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

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getExamplesJson() {
        return examplesJson;
    }

    public void setExamplesJson(String examplesJson) {
        this.examplesJson = examplesJson;
    }

    public String getKnowledgeBaseNo() {
        return knowledgeBaseNo;
    }

    public void setKnowledgeBaseNo(String knowledgeBaseNo) {
        this.knowledgeBaseNo = knowledgeBaseNo;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getMcpToolId() {
        return mcpToolId;
    }

    public void setMcpToolId(String mcpToolId) {
        this.mcpToolId = mcpToolId;
    }

    public String getPromptSnippet() {
        return promptSnippet;
    }

    public void setPromptSnippet(String promptSnippet) {
        this.promptSnippet = promptSnippet;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getParamPromptTemplate() {
        return paramPromptTemplate;
    }

    public void setParamPromptTemplate(String paramPromptTemplate) {
        this.paramPromptTemplate = paramPromptTemplate;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public BigDecimal getMinScore() {
        return minScore;
    }

    public void setMinScore(BigDecimal minScore) {
        this.minScore = minScore;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
