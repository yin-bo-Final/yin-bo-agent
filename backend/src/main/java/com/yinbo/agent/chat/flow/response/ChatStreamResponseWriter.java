package com.yinbo.agent.chat.flow.response;

import com.yinbo.agent.chat.dto.ChatStreamEvent;
import com.yinbo.agent.chat.dto.ConversationMemorySummaryResponse;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
// 会话 SSE 流式响应写出服务。
public class ChatStreamResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamResponseWriter.class);

    // 发送流式开始事件。
    public void sendStart(ChatExecutionContext ctx) {
        sendEvent(ctx.emitter(), "start", ChatStreamEvent.start(
                ctx.conversation().getConversationNo(),
                ctx.model().id(),
                ConversationMemorySummaryResponse.from(ctx.memorySummary())
        ));
    }

    // 发送模型增量内容。
    public void sendDelta(ChatExecutionContext ctx, String delta) {
        sendEvent(ctx.emitter(), "delta", ChatStreamEvent.delta(
                ctx.conversation().getConversationNo(),
                ctx.model().id(),
                delta
        ));
    }

    // 发送完整文本的分片内容。
    public void sendChunkedContent(ChatExecutionContext ctx, String content) {
        int chunkSize = 24;
        for (int start = 0; start < content.length(); start += chunkSize) {
            String delta = content.substring(start, Math.min(start + chunkSize, content.length()));
            sendDelta(ctx, delta);
        }
    }

    // 发送流式完成事件。
    public void sendDone(ChatExecutionContext ctx) {
        sendEvent(ctx.emitter(), "done", ChatStreamEvent.done(
                ctx.conversation().getConversationNo(),
                ctx.chatResponse().modelId(),
                ctx.chatResponse().createdAt(),
                ctx.chatResponse().responseDurationMs(),
                ctx.chatResponse().totalTokens()
        ));
    }

    // 发送流式错误事件。
    public void sendError(ChatExecutionContext ctx, String message) {
        sendEvent(ctx.emitter(), "error", ChatStreamEvent.error(message));
    }

    // 安全结束 SSE 连接。
    public void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            log.debug("SSE emitter already completed.");
        }
    }

    // 发送单个 SSE 事件。
    private void sendEvent(SseEmitter emitter, String eventName, ChatStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(event));
        } catch (Exception exception) {
            if (isClientDisconnected(exception)) {
                throw new ClientDisconnectedException(exception);
            }
            throw new IllegalStateException("SSE event send failed", exception);
        }
    }

    // 判断异常是否来自客户端断开连接。
    private boolean isClientDisconnected(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("ClientAbortException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // 客户端断开连接异常。
    public static class ClientDisconnectedException extends RuntimeException {

        public ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
