package com.yinbo.agent.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

// 管理后台意图识别记录响应。
public record IntentResolveRecordResponse(
        String id,
        String conversationId,
        String userId,
        String userMessageId,
        String originalQuery,
        String normalizedQuery,
        String rewrittenQuery,
        JsonNode subQuestions,
        JsonNode intents,
        JsonNode selectedNodes,
        JsonNode subQuestionIntents,
        String modelId,
        Boolean ambiguous,
        String guidanceQuestion,
        String outcome,
        String fallbackReason,
        Boolean success,
        String errorMessage,
        Long durationMs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
