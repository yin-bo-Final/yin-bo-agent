package com.yinbo.agent.chat.flow;

import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.flow.clarification.ClarificationService;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.intent.IntentResolutionService;
import com.yinbo.agent.chat.flow.lifecycle.ConversationLifecycleService;
import com.yinbo.agent.chat.flow.lifecycle.ConversationStreamRegistry;
import com.yinbo.agent.chat.flow.llm.DirectChatService;
import com.yinbo.agent.chat.flow.memory.ConversationMemoryCompressionService;
import com.yinbo.agent.chat.flow.memory.ConversationMemoryService;
import com.yinbo.agent.chat.flow.message.AssistantResponseResult;
import com.yinbo.agent.chat.flow.message.ChatMessagePersistenceService;
import com.yinbo.agent.chat.flow.query.QueryRewriteService;
import com.yinbo.agent.chat.flow.response.ChatStreamResponseWriter;
import com.yinbo.agent.chat.flow.response.ChatStreamResponseWriter.ClientDisconnectedException;
import com.yinbo.agent.chat.flow.retrieval.RetrievalContext;
import com.yinbo.agent.chat.flow.retrieval.RetrievalExecuteService;
import com.yinbo.agent.common.BusinessException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
// 会话处理流水线编排器。
public class ConversationFlowExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConversationFlowExecutor.class);
    private static final long STREAM_TIMEOUT_MILLIS = 180_000L;

    private final ConversationLifecycleService conversationLifecycleService;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationMemoryCompressionService conversationMemoryCompressionService;
    private final ChatMessagePersistenceService messagePersistenceService;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolutionService intentResolutionService;
    private final ClarificationService clarificationService;
    private final RetrievalExecuteService retrievalExecuteService;
    private final DirectChatService directChatService;
    private final ChatStreamResponseWriter streamResponseWriter;
    private final ConversationStreamRegistry streamRegistry;

    // 注入会话流水线各阶段服务。
    public ConversationFlowExecutor(
            ConversationLifecycleService conversationLifecycleService,
            ConversationMemoryService conversationMemoryService,
            ConversationMemoryCompressionService conversationMemoryCompressionService,
            ChatMessagePersistenceService messagePersistenceService,
            QueryRewriteService queryRewriteService,
            IntentResolutionService intentResolutionService,
            ClarificationService clarificationService,
            RetrievalExecuteService retrievalExecuteService,
            DirectChatService directChatService,
            ChatStreamResponseWriter streamResponseWriter,
            ConversationStreamRegistry streamRegistry
    ) {
        this.conversationLifecycleService = conversationLifecycleService;
        this.conversationMemoryService = conversationMemoryService;
        this.conversationMemoryCompressionService = conversationMemoryCompressionService;
        this.messagePersistenceService = messagePersistenceService;
        this.queryRewriteService = queryRewriteService;
        this.intentResolutionService = intentResolutionService;
        this.clarificationService = clarificationService;
        this.retrievalExecuteService = retrievalExecuteService;
        this.directChatService = directChatService;
        this.streamResponseWriter = streamResponseWriter;
        this.streamRegistry = streamRegistry;
    }

    @Transactional
    // 执行普通非流式会话流水线。
    public ChatResponse executeSync(ChatExecutionContext ctx) {
        prepareRequest(ctx);

        queryRewriteService.rewrite(ctx);
        intentResolutionService.resolve(ctx);

        String guidanceMessage = clarificationService.guidanceMessage(ctx);
        if (guidanceMessage != null) {
            return completeWithStaticAssistantMessage(ctx, guidanceMessage);
        }
        if (intentResolutionService.isDirectChat(ctx)) {
            return completeWithAssistantResult(ctx, directChatService.generate(ctx), "sync");
        }

        RetrievalContext retrievalContext = retrievalExecuteService.retrieve(ctx);
        String emptyRetrievalMessage = retrievalExecuteService.emptyRetrievalMessage(retrievalContext);
        if (emptyRetrievalMessage != null) {
            return completeWithStaticAssistantMessage(ctx, emptyRetrievalMessage);
        }
        return generateGroundedResponse(ctx, retrievalContext);
    }

    // 启动 SSE 流式会话流水线。
    public SseEmitter executeStream(ChatExecutionContext ctx) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        ctx.setEmitter(emitter);
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        CompletableFuture.runAsync(() -> {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                doExecuteStream(ctx);
            } finally {
                MDC.clear();
            }
        });
        return emitter;
    }

    // 执行流式会话主流程。
    private void doExecuteStream(ChatExecutionContext ctx) {
        String conversationId = null;
        Long conversationPk = null;
        boolean streamRegistered = false;
        try {
            prepareRequest(ctx);
            conversationId = ctx.conversation().getConversationNo();
            conversationPk = ctx.conversation().getId();
            streamRegistry.markStarted(conversationPk);
            streamRegistered = true;
            streamResponseWriter.sendStart(ctx);

            queryRewriteService.rewrite(ctx);
            intentResolutionService.resolve(ctx);

            String guidanceMessage = clarificationService.guidanceMessage(ctx);
            if (guidanceMessage != null) {
                completeWithStaticAssistantMessage(ctx, guidanceMessage);
                return;
            }
            if (intentResolutionService.isDirectChat(ctx)) {
                AssistantResponseResult result = directChatService.stream(ctx);
                if (result == null) {
                    return;
                }
                completeWithAssistantResult(ctx, result, "stream");
                finishStream(ctx);
                return;
            }

            RetrievalContext retrievalContext = retrievalExecuteService.retrieve(ctx);
            String emptyRetrievalMessage = retrievalExecuteService.emptyRetrievalMessage(retrievalContext);
            if (emptyRetrievalMessage != null) {
                completeWithStaticAssistantMessage(ctx, emptyRetrievalMessage);
                return;
            }
            streamGroundedResponse(ctx, retrievalContext);
        } catch (ClientDisconnectedException exception) {
            log.info(
                    "event=ai_stream_disconnected conversationId={} modelId={}",
                    conversationId,
                    ctx.model() == null ? "-" : ctx.model().id()
            );
            streamResponseWriter.safeComplete(ctx.emitter());
        } catch (BusinessException exception) {
            log.warn(
                    "event=ai_stream_business_failed conversationId={} modelId={} status={} message={}",
                    conversationId,
                    ctx.model() == null ? "-" : ctx.model().id(),
                    exception.getStatus().value(),
                    sanitizeLogValue(exception.getMessage())
            );
            try {
                streamResponseWriter.sendError(ctx, exception.getMessage());
            } catch (ClientDisconnectedException ignored) {
                log.info(
                        "event=ai_stream_disconnected_before_business_error conversationId={} modelId={}",
                        conversationId,
                        ctx.model() == null ? "-" : ctx.model().id()
                );
            }
            streamResponseWriter.safeComplete(ctx.emitter());
        } catch (Exception exception) {
            log.warn(
                    "event=ai_stream_failed conversationId={} modelId={} type={} message={}",
                    conversationId,
                    ctx.model() == null ? "-" : ctx.model().id(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            try {
                streamResponseWriter.sendError(ctx, "流式响应失败了，请稍后重试。");
            } catch (ClientDisconnectedException ignored) {
                log.info(
                        "event=ai_stream_disconnected_before_error conversationId={} modelId={}",
                        conversationId,
                        ctx.model() == null ? "-" : ctx.model().id()
                );
            }
            streamResponseWriter.safeComplete(ctx.emitter());
        } finally {
            if (streamRegistered) {
                streamRegistry.markFinished(conversationPk);
            }
        }
    }

    // 准备会话、加载记忆并保存本轮用户消息。
    private void prepareRequest(ChatExecutionContext ctx) {
        conversationLifecycleService.prepare(ctx);
        if (conversationMemoryCompressionService.isCompressing(ctx.conversation().getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前会话正在压缩，结束后再发送");
        }
        conversationMemoryService.load(ctx);
        messagePersistenceService.persistCurrentUserMessage(ctx);
        conversationMemoryCompressionService.preparePromptMemory(ctx);
    }

    // 生成基于检索上下文的普通响应，后续实现。
    private ChatResponse generateGroundedResponse(ChatExecutionContext ctx, RetrievalContext retrievalContext) {
        return completeWithStaticAssistantMessage(ctx, "RAG 回答流程还未实现。");
    }

    // 生成基于检索上下文的流式响应，后续实现。
    private void streamGroundedResponse(ChatExecutionContext ctx, RetrievalContext retrievalContext) {
        completeWithStaticAssistantMessage(ctx, "RAG 回答流程还未实现。");
    }

    // 使用固定内容完成一次 assistant 响应。
    private ChatResponse completeWithStaticAssistantMessage(ChatExecutionContext ctx, String content) {
        if (ctx.streamMode()) {
            streamResponseWriter.sendChunkedContent(ctx, content);
        }
        ChatResponse response = completeWithAssistantResult(ctx, directChatService.staticResult(ctx, content), ctx.streamMode() ? "stream" : "sync");
        if (ctx.streamMode()) {
            finishStream(ctx);
        }
        return response;
    }

    // 保存 assistant 响应并生成接口返回值。
    private ChatResponse completeWithAssistantResult(ChatExecutionContext ctx, AssistantResponseResult result, String mode) {
        return messagePersistenceService.completeAssistantMessage(ctx, result, mode);
    }

    // 发送流式完成事件并关闭连接。
    private void finishStream(ChatExecutionContext ctx) {
        streamResponseWriter.sendDone(ctx);
        streamResponseWriter.safeComplete(ctx.emitter());
    }

    // 清洗日志字段值。
    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
