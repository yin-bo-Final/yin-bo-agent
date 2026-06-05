package com.yinbo.agent.chat.flow.llm;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.message.AssistantResponseResult;
import com.yinbo.agent.chat.flow.prompt.PromptAssemblyService;
import com.yinbo.agent.chat.flow.response.ChatStreamResponseWriter;
import com.yinbo.agent.chat.flow.response.ChatStreamResponseWriter.ClientDisconnectedException;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.model.ModelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
// 直接 LLM 对话服务，负责普通直聊和流式直聊模型调用。
public class DirectChatService {

    private static final Logger log = LoggerFactory.getLogger(DirectChatService.class);

    private final LLMService llmService;
    private final PromptAssemblyService promptAssemblyService;
    private final ChatStreamResponseWriter streamResponseWriter;

    // 注入 LLM 服务、Prompt 组装服务和流式写出服务。
    public DirectChatService(
            LLMService llmService,
            PromptAssemblyService promptAssemblyService,
            ChatStreamResponseWriter streamResponseWriter
    ) {
        this.llmService = llmService;
        this.promptAssemblyService = promptAssemblyService;
        this.streamResponseWriter = streamResponseWriter;
    }

    // 执行普通直聊模型调用。
    public AssistantResponseResult generate(ChatExecutionContext ctx) {
        String conversationId = ctx.conversation().getConversationNo();
        long responseStartedAt = System.nanoTime();
        try {
            LLMResponse response = llmService.chat(promptAssemblyService.buildDirectRequest(ctx));
            return toAssistantResponseResult(response, responseStartedAt, ctx.latestUserMessage().content(), ctx.model().id());
        } catch (Exception exception) {
            log.warn(
                    "event=ai_call_failed mode=sync conversationId={} modelId={} type={} message={}",
                    conversationId,
                    ctx.model().id(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            return modelFailureResult(ctx, responseStartedAt);
        }
    }

    // 执行流式直聊模型调用，返回 null 表示已中断并完成 SSE。
    public AssistantResponseResult stream(ChatExecutionContext ctx) {
        String conversationId = ctx.conversation().getConversationNo();
        long responseStartedAt = System.nanoTime();
        StringBuilder contentBuilder = new StringBuilder();
        AssistantResponseResult result;
        try {
            LLMResponse response = llmService.streamChat(
                    promptAssemblyService.buildDirectRequest(ctx),
                    delta -> {
                        if (delta == null || delta.isEmpty()) {
                            return;
                        }
                        contentBuilder.append(delta);
                        streamResponseWriter.sendDelta(ctx, delta);
                    }
            );
            String content = contentBuilder.isEmpty()
                    ? response == null ? "" : response.content()
                    : contentBuilder.toString();
            long responseDurationMs = elapsedMillis(responseStartedAt);
            String modelId = response == null || response.modelId() == null ? ctx.model().id() : response.modelId();
            result = toAssistantResponseResult(modelId, content, responseDurationMs, usageFrom(response), ctx.latestUserMessage().content());
        } catch (ClientDisconnectedException exception) {
            throw exception;
        } catch (Exception exception) {
            if (!contentBuilder.isEmpty()) {
                log.warn(
                        "event=ai_stream_interrupted_after_delta conversationId={} modelId={} type={} message={}",
                        conversationId,
                        ctx.model().id(),
                        exception.getClass().getSimpleName(),
                        sanitizeLogValue(exception.getMessage()),
                        exception
                );
                try {
                    streamResponseWriter.sendError(ctx, "流式响应中断了，请重新发起对话。");
                } catch (ClientDisconnectedException ignored) {
                    // 客户端已断开时不再写响应。
                }
                streamResponseWriter.safeComplete(ctx.emitter());
                return null;
            }
            log.warn(
                    "event=ai_call_failed mode=stream conversationId={} modelId={} type={} message={}",
                    conversationId,
                    ctx.model().id(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            result = modelFailureResult(ctx, responseStartedAt);
            streamResponseWriter.sendChunkedContent(ctx, result.content());
        }

        if (result.content().isBlank()) {
            String content = "模型调用成功，但返回内容为空。";
            streamResponseWriter.sendDelta(ctx, content);
            return result.withContent(content);
        }
        return result;
    }

    // 生成固定内容的 assistant 响应结果。
    public AssistantResponseResult staticResult(ChatExecutionContext ctx, String content) {
        long responseStartedAt = System.nanoTime();
        return new AssistantResponseResult(
                ctx.model().id(),
                content,
                elapsedMillis(responseStartedAt),
                null,
                estimateTokenCount(content),
                estimateTokenCount(ctx.latestUserMessage().content()) + estimateTokenCount(content)
        );
    }

    // 构造模型失败时的调用结果。
    private AssistantResponseResult modelFailureResult(ChatExecutionContext ctx, long responseStartedAt) {
        String content = modelFailureResponseContent(ctx.model(), ctx.latestUserMessage().content());
        long responseDurationMs = elapsedMillis(responseStartedAt);
        return new AssistantResponseResult(
                ctx.model().id(),
                content,
                responseDurationMs,
                null,
                estimateTokenCount(content),
                estimateTokenCount(ctx.latestUserMessage().content()) + estimateTokenCount(content)
        );
    }

    // 转换模型响应为 assistant 响应结果。
    private AssistantResponseResult toAssistantResponseResult(
            LLMResponse response,
            long responseStartedAt,
            String latestUserContent,
            String fallbackModelId
    ) {
        String modelId = response == null || response.modelId() == null ? fallbackModelId : response.modelId();
        String text = response == null ? null : response.content();
        String content = text == null || text.isBlank() ? "模型调用成功，但返回内容为空。" : text;
        long responseDurationMs = elapsedMillis(responseStartedAt);
        return toAssistantResponseResult(modelId, content, responseDurationMs, usageFrom(response), latestUserContent);
    }

    // 转换模型调用结果。
    private AssistantResponseResult toAssistantResponseResult(
            String modelId,
            String content,
            long responseDurationMs,
            TokenUsage tokenUsage,
            String latestUserContent
    ) {
        Integer promptTokens = tokenUsage == null ? null : tokenUsage.promptTokens();
        Integer completionTokens = tokenUsage == null ? null : tokenUsage.completionTokens();
        Integer totalTokens = tokenUsage == null ? null : tokenUsage.totalTokens();
        int estimatedCompletionTokens = estimateTokenCount(content);
        if (completionTokens == null) {
            completionTokens = estimatedCompletionTokens;
        }
        if (totalTokens == null) {
            totalTokens = (promptTokens == null ? estimateTokenCount(latestUserContent) : promptTokens) + completionTokens;
        }
        return new AssistantResponseResult(modelId, content, responseDurationMs, promptTokens, completionTokens, totalTokens);
    }

    // 从模型响应中提取 token 用量。
    private TokenUsage usageFrom(LLMResponse response) {
        if (response == null || response.usage() == null) {
            return null;
        }
        LLMResponse.TokenUsage usage = response.usage();
        return new TokenUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    }

    // 构造模型失败时的兜底回答。
    private String modelFailureResponseContent(ModelOption model, String latestUserMessage) {
        return """
                我已经收到你的消息：%s

                当前选择模型：%s（%s）

                这次模型调用失败了。通常是模型服务网络波动、连接被重置，或者上游暂时不可用。
                你可以先点击“新对话”重试一次；如果还是失败，稍后再试会更稳。
                """.formatted(latestUserMessage, model.name(), model.id());
    }

    // 粗略估算 token 数。
    private int estimateTokenCount(String content) {
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

    // 计算已经消耗的毫秒数。
    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    // 清洗日志字段值。
    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    // Token 用量。
    private record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }
}
