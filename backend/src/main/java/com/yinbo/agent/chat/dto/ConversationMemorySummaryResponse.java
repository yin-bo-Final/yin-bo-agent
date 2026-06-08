package com.yinbo.agent.chat.dto;

import com.yinbo.agent.chat.entity.ConversationMemorySummary;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 会话记忆活跃摘要响应。
public record ConversationMemorySummaryResponse(
        Long coveredStartMessageId,
        Long coveredEndMessageId,
        Integer sourceMessageCount,
        Integer summaryTokens,
        Instant createdAt
) {

    // 从摘要实体转换为前端可恢复的摘要水位线。
    public static ConversationMemorySummaryResponse from(ConversationMemorySummary summary) {
        if (summary == null) {
            return null;
        }
        return new ConversationMemorySummaryResponse(
                summary.getCoveredStartMessageId(),
                summary.getCoveredEndMessageId(),
                summary.getSourceMessageCount(),
                summary.getSummaryTokens(),
                toInstant(summary.getCreatedAt())
        );
    }

    // 转换为前端统一使用的时间类型。
    private static Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
