package com.yinbo.agent.chat.dto;

import java.time.Instant;

public record ConversationSummaryResponse(
        String conversationId,
        String title,
        String modelId,
        Instant lastMessageAt,
        Instant createdAt
) {
}
