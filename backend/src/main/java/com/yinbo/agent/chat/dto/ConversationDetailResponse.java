package com.yinbo.agent.chat.dto;

import java.time.Instant;
import java.util.List;

public record ConversationDetailResponse(
        String conversationId,
        String title,
        String modelId,
        Instant createdAt,
        List<ConversationMessageResponse> messages
) {
}
