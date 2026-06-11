package com.yinbo.agent.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("chat_intent_resolve_record")
// 意图识别流水线中间产物记录。
public class ChatIntentResolveRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("user_id")
    private Long userId;

    @TableField("user_message_id")
    private Long userMessageId;

    @TableField("original_query")
    private String originalQuery;

    @TableField("normalized_query")
    private String normalizedQuery;

    @TableField("rewritten_query")
    private String rewrittenQuery;

    @TableField("sub_questions_json")
    private String subQuestionsJson;

    @TableField("intents_json")
    private String intentsJson;

    @TableField("selected_nodes_json")
    private String selectedNodesJson;

    @TableField("sub_question_intents_json")
    private String subQuestionIntentsJson;

    @TableField("model_id")
    private String modelId;

    private Boolean ambiguous;

    @TableField("guidance_question")
    private String guidanceQuestion;

    private String outcome;

    @TableField("fallback_reason")
    private String fallbackReason;

    private Boolean success;

    @TableField("error_message")
    private String errorMessage;

    @TableField("duration_ms")
    private Long durationMs;

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

    public Long getUserMessageId() {
        return userMessageId;
    }

    public void setUserMessageId(Long userMessageId) {
        this.userMessageId = userMessageId;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public void setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    public String getSubQuestionsJson() {
        return subQuestionsJson;
    }

    public void setSubQuestionsJson(String subQuestionsJson) {
        this.subQuestionsJson = subQuestionsJson;
    }

    public String getIntentsJson() {
        return intentsJson;
    }

    public void setIntentsJson(String intentsJson) {
        this.intentsJson = intentsJson;
    }

    public String getSelectedNodesJson() {
        return selectedNodesJson;
    }

    public void setSelectedNodesJson(String selectedNodesJson) {
        this.selectedNodesJson = selectedNodesJson;
    }

    public String getSubQuestionIntentsJson() {
        return subQuestionIntentsJson;
    }

    public void setSubQuestionIntentsJson(String subQuestionIntentsJson) {
        this.subQuestionIntentsJson = subQuestionIntentsJson;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Boolean getAmbiguous() {
        return ambiguous;
    }

    public void setAmbiguous(Boolean ambiguous) {
        this.ambiguous = ambiguous;
    }

    public String getGuidanceQuestion() {
        return guidanceQuestion;
    }

    public void setGuidanceQuestion(String guidanceQuestion) {
        this.guidanceQuestion = guidanceQuestion;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
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
