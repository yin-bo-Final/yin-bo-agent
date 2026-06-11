package com.yinbo.agent.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

// 管理后台查询改写记录响应。
public record QueryRewriteRecordResponse(
        String id,
        String conversationId,
        String userId,
        String userMessageId,
        String originalQuery,
        String normalizedQuery,
        String rewrittenQuery,
        JsonNode subQuestions,
        JsonNode matchedTerms,
        String modelId,
        String promptVersion,
        String sourceType,
        String fallbackReason,
        Boolean success,
        String errorMessage,
        String rawModelResponse,
        Long durationMs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
