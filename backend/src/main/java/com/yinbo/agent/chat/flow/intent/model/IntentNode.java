package com.yinbo.agent.chat.flow.intent.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 运行时意图树节点。
public class IntentNode {

    private String id;
    private String dbId;
    private String parentId;
    private String name;
    private String description;
    private IntentLevel level;
    private IntentKind kind;
    private List<String> examples = new ArrayList<>();
    private List<IntentNode> children = new ArrayList<>();
    private String fullPath;
    private String knowledgeBaseNo;
    private String collectionName;
    private String mcpToolId;
    private Integer topK;
    private Double minScore;
    private String promptSnippet;
    private String promptTemplate;
    private String paramPromptTemplate;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 判断是否叶子节点，只有叶子节点参与意图分类。
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    public boolean isKB() {
        return kind == null || kind == IntentKind.KB;
    }

    public boolean isMCP() {
        return kind == IntentKind.MCP;
    }

    public boolean isSystem() {
        return kind == IntentKind.SYSTEM;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String dbId) {
        this.dbId = dbId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
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

    public IntentLevel getLevel() {
        return level;
    }

    public void setLevel(IntentLevel level) {
        this.level = level;
    }

    public IntentKind getKind() {
        return kind;
    }

    public void setKind(IntentKind kind) {
        this.kind = kind;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples == null ? new ArrayList<>() : new ArrayList<>(examples);
    }

    public List<IntentNode> getChildren() {
        return children;
    }

    public void setChildren(List<IntentNode> children) {
        this.children = children == null ? new ArrayList<>() : new ArrayList<>(children);
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
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

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
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
