package com.yinbo.agent.chat.dto;

import java.time.Instant;

public record ConversationSummaryResponse(
        String conversationId,
        String title,
        String modelId,
        boolean pinned,
        Instant pinnedAt,
        Instant lastMessageAt,
        Instant createdAt
) {
}
