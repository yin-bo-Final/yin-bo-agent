package com.yinbo.agent.chat.flow.memory;

import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
// 会话 Prompt token 粗略估算器。
public class ConversationTokenEstimator {

    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    // 估算一组缓存消息的 token 数。
    public int estimateMessages(List<CachedChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (CachedChatMessage message : messages) {
            total += MESSAGE_OVERHEAD_TOKENS + estimateText(message.role()) + estimateText(message.content());
        }
        return total;
    }

    // 粗略估算文本 token 数。
    public int estimateText(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int asciiChars = 0;
        int nonAsciiChars = 0;
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) <= 127) {
                asciiChars++;
            } else {
                nonAsciiChars++;
            }
        }
        return Math.max(1, (int) Math.ceil(asciiChars / 4.0 + nonAsciiChars / 1.8));
    }
}
