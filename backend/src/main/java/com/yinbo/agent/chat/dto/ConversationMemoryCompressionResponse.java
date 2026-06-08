package com.yinbo.agent.chat.dto;

import java.time.Instant;

// 会话记忆压缩响应。
public record ConversationMemoryCompressionResponse(
        String conversationId,
        boolean compressed,
        String triggerType,
        Long coveredStartMessageId,
        Long coveredEndMessageId,
        Integer sourceMessageCount,
        Integer summaryTokens,
        Instant createdAt,
        String message
) {
}
