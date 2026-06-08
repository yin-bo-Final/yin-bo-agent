package com.yinbo.agent.chat.flow.lifecycle;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
// 当前 service 实例内的会话流式输出状态登记器。
public class ConversationStreamRegistry {

    private final ConcurrentMap<Long, AtomicInteger> activeStreams = new ConcurrentHashMap<>();

    // 标记指定会话开始流式输出。
    public void markStarted(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        activeStreams.computeIfAbsent(conversationId, key -> new AtomicInteger()).incrementAndGet();
    }

    // 标记指定会话结束流式输出。
    public void markFinished(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        activeStreams.computeIfPresent(conversationId, (key, counter) -> counter.decrementAndGet() <= 0 ? null : counter);
    }

    // 判断指定会话当前是否正在流式输出。
    public boolean isStreaming(Long conversationId) {
        AtomicInteger counter = activeStreams.get(conversationId);
        return counter != null && counter.get() > 0;
    }
}
