package com.yinbo.agent.chat.flow.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse.DurationStageTrace;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse.IntentResolveTrace;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse.NodeTrace;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse.QueryRewriteTrace;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse.RagTrace;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse.SubQuestionIntentTrace;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse.TermTrace;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.flow.context.ChatIntentType;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext.DurationStage;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentResolveResult;
import com.yinbo.agent.chat.flow.intent.model.NodeScore;
import com.yinbo.agent.chat.flow.query.QueryRewriteResult;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyMatch;
import com.yinbo.agent.chat.flow.lifecycle.ConversationLifecycleService;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import com.yinbo.agent.chat.service.ChatMessageCacheService;
import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
// 会话消息持久化服务，负责保存消息并刷新会话记忆缓存。
public class ChatMessagePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessagePersistenceService.class);

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageCacheService chatMessageCacheService;
    private final ConversationLifecycleService conversationLifecycleService;
    private final ObjectMapper objectMapper;

    // 注入消息 Mapper、缓存服务和会话生命周期服务。
    public ChatMessagePersistenceService(
            ChatMessageMapper chatMessageMapper,
            ChatMessageCacheService chatMessageCacheService,
            ConversationLifecycleService conversationLifecycleService,
            ObjectMapper objectMapper
    ) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageCacheService = chatMessageCacheService;
        this.conversationLifecycleService = conversationLifecycleService;
        this.objectMapper = objectMapper;
    }

    // 保存本轮用户消息并写入会话记忆。
    public void persistCurrentUserMessage(ChatExecutionContext ctx) {
        ChatMessageEntity userMessage = persistMessage(
                ctx.conversation().getId(),
                ctx.authUser().getId(),
                "user",
                ctx.latestUserMessage().content(),
                ctx.model().id()
        );
        ctx.setUserMessage(userMessage);
        ctx.setConversationMessages(appendCachedMessage(ctx.conversationMessages(), userMessage));
        if (ctx.streamMode()) {
            putConversationMessagesAfterCommit(ctx.authUser().getId(), ctx.conversation().getId(), ctx.conversationMessages());
        }
    }

    // 保存 assistant 消息、更新会话并构造响应。
    public ChatResponse completeAssistantMessage(ChatExecutionContext ctx, AssistantResponseResult result, String mode) {
        Long totalResponseDurationMs = ctx.markTotalResponseDuration();
        ChatAssistantTraceResponse assistantTrace = buildAssistantTrace(ctx, result, totalResponseDurationMs);
        String assistantTraceJson = toTraceJson(ctx, assistantTrace);
        ChatMessageEntity assistantMessage = persistMessage(
                ctx.conversation().getId(),
                ctx.authUser().getId(),
                "assistant",
                result.content(),
                result.modelId(),
                totalResponseDurationMs,
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens(),
                assistantTraceJson
        );
        ctx.setConversationMessages(appendCachedMessage(ctx.conversationMessages(), assistantMessage));
        conversationLifecycleService.touch(ctx, result.modelId(), assistantMessage.getCreatedAt());
        putConversationMessagesAfterCommit(ctx.authUser().getId(), ctx.conversation().getId(), ctx.conversationMessages());
        log.info(
                "event={} mode={} userId={} conversationId={} modelId={} sourceType={} success={} fallbackReason={} durationMs={} llmDurationMs={} promptTokens={} completionTokens={} totalTokens={}",
                result.success() ? "ai_chat_completed" : "ai_chat_fallback_completed",
                mode,
                ctx.authUser().getId(),
                ctx.conversation().getConversationNo(),
                result.modelId(),
                result.sourceType(),
                result.success(),
                result.fallbackReason() == null ? "-" : result.fallbackReason(),
                totalResponseDurationMs,
                result.responseDurationMs(),
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens()
        );
        ChatResponse response = new ChatResponse(
                ctx.conversation().getConversationNo(),
                result.modelId(),
                "assistant",
                result.content(),
                toInstant(assistantMessage.getCreatedAt()),
                totalResponseDurationMs,
                result.totalTokens(),
                assistantTrace
        );
        ctx.setChatResponse(response);
        return response;
    }

    // 保存普通消息。
    private ChatMessageEntity persistMessage(
            Long conversationId,
            Long userId,
            String role,
            String content,
            String modelId
    ) {
        return persistMessage(conversationId, userId, role, content, modelId, null, null, null, null, null);
    }

    // 保存带统计信息的消息。
    private ChatMessageEntity persistMessage(
            Long conversationId,
            Long userId,
            String role,
            String content,
            String modelId,
            Long responseDurationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String assistantTraceJson
    ) {
        ChatMessageEntity messageEntity = new ChatMessageEntity();
        messageEntity.setConversationId(conversationId);
        messageEntity.setUserId(userId);
        messageEntity.setRole(role);
        messageEntity.setContent(content);
        messageEntity.setModelId(modelId);
        messageEntity.setResponseDurationMs(responseDurationMs);
        messageEntity.setPromptTokens(promptTokens);
        messageEntity.setCompletionTokens(completionTokens);
        messageEntity.setTotalTokens(totalTokens);
        messageEntity.setAssistantTraceJson(assistantTraceJson);
        chatMessageMapper.insert(messageEntity);
        return messageEntity;
    }

    private ChatAssistantTraceResponse buildAssistantTrace(
            ChatExecutionContext ctx,
            AssistantResponseResult result,
            Long totalResponseDurationMs
    ) {
        Long otherDurationMs = otherDurationMs(ctx, result, totalResponseDurationMs);
        return new ChatAssistantTraceResponse(
                ChatAssistantTraceResponse.CURRENT_TRACE_VERSION,
                result.modelId(),
                totalResponseDurationMs,
                result.responseDurationMs(),
                otherDurationMs,
                result.totalTokens(),
                ctx.enteredRag(),
                firstPresent(
                        result.fallbackReason(),
                        ctx.rewriteResult() == null ? null : ctx.rewriteResult().fallbackReason(),
                        ctx.intentResult() == null ? null : ctx.intentResult().fallbackReason()
                ),
                buildDurationStages(ctx, result, otherDurationMs),
                buildRewriteTrace(ctx),
                buildIntentTrace(ctx),
                buildRagTrace(ctx)
        );
    }

    private Long otherDurationMs(
            ChatExecutionContext ctx,
            AssistantResponseResult result,
            Long totalResponseDurationMs
    ) {
        if (totalResponseDurationMs == null) {
            return null;
        }
        long knownDurationMs = ctx.durationStages().stream()
                .mapToLong(stage -> durationValue(stage.durationMs()))
                .sum();
        if (shouldIncludeLlmStage(result)) {
            knownDurationMs += durationValue(result.responseDurationMs());
        }
        return Math.max(0L, totalResponseDurationMs - knownDurationMs);
    }

    private long durationValue(Long durationMs) {
        return durationMs == null ? 0L : Math.max(0L, durationMs);
    }

    private List<DurationStageTrace> buildDurationStages(
            ChatExecutionContext ctx,
            AssistantResponseResult result,
            Long otherDurationMs
    ) {
        List<DurationStageTrace> stages = new ArrayList<>();
        for (DurationStage stage : ctx.durationStages()) {
            stages.add(new DurationStageTrace(stage.code(), stage.label(), stage.durationMs()));
        }
        if (shouldIncludeLlmStage(result)) {
            stages.add(new DurationStageTrace("llm", "LLM", result.responseDurationMs()));
        }
        stages.add(new DurationStageTrace("other", "其他", otherDurationMs));
        return stages;
    }

    private boolean shouldIncludeLlmStage(AssistantResponseResult result) {
        return result != null && !"STATIC".equalsIgnoreCase(result.sourceType());
    }

    private QueryRewriteTrace buildRewriteTrace(ChatExecutionContext ctx) {
        QueryRewriteResult result = ctx.rewriteResult();
        if (result == null) {
            return null;
        }
        return new QueryRewriteTrace(
                ctx.originalQuery(),
                result.normalizedQuery(),
                result.rewrite(),
                result.shouldSplit(),
                result.subQuestions(),
                result.matchedTerms().stream().map(this::toTermTrace).toList(),
                result.sourceType(),
                result.success(),
                result.fallbackReason(),
                ctx.queryRewriteDurationMs()
        );
    }

    private TermTrace toTermTrace(TerminologyMatch match) {
        return new TermTrace(match.raw(), match.canonical(), match.termType());
    }

    private IntentResolveTrace buildIntentTrace(ChatExecutionContext ctx) {
        IntentResolveResult result = ctx.intentResult();
        if (result == null) {
            return null;
        }
        return new IntentResolveTrace(
                ctx.intents().stream().map(ChatIntentType::name).toList(),
                result.outcome().name(),
                result.ambiguous(),
                result.guidanceQuestion(),
                result.selectedNodeScores().stream().map(this::toNodeTrace).toList(),
                result.subQuestionIntents().stream()
                        .map(item -> new SubQuestionIntentTrace(
                                item.subQuestion(),
                                item.nodeScores().stream().map(this::toNodeTrace).toList()
                        ))
                        .toList(),
                result.success(),
                result.fallbackReason(),
                ctx.intentResolveDurationMs()
        );
    }

    private NodeTrace toNodeTrace(NodeScore score) {
        IntentNode node = score.node();
        return new NodeTrace(
                node == null ? null : node.getId(),
                node == null ? null : node.getFullPath(),
                node == null || node.getKind() == null ? null : node.getKind().name(),
                score.score(),
                score.source(),
                score.reason()
        );
    }

    private RagTrace buildRagTrace(ChatExecutionContext ctx) {
        return new RagTrace(
                ctx.enteredRag(),
                ctx.ragKnowledgeSnippetCount(),
                ctx.ragToolResultCount(),
                ctx.ragDurationMs()
        );
    }

    private String toTraceJson(ChatExecutionContext ctx, ChatAssistantTraceResponse assistantTrace) {
        try {
            return objectMapper.writeValueAsString(assistantTrace);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "event=assistant_trace_encode_failed conversationId={} type={} message={}",
                    ctx.conversation() == null ? "-" : ctx.conversation().getConversationNo(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
            return null;
        }
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // 追加一条缓存消息。
    private List<CachedChatMessage> appendCachedMessage(
            List<CachedChatMessage> conversationMessages,
            ChatMessageEntity message
    ) {
        List<CachedChatMessage> updatedMessages = new ArrayList<>(conversationMessages);
        updatedMessages.add(CachedChatMessage.from(message));
        return updatedMessages;
    }

    // 事务提交后写入会话消息缓存。
    private void putConversationMessagesAfterCommit(
            Long userId,
            Long conversationId,
            List<CachedChatMessage> conversationMessages
    ) {
        List<CachedChatMessage> snapshot = List.copyOf(conversationMessages);
        runAfterCommit(() -> chatMessageCacheService.putMessages(userId, conversationId, snapshot));
    }

    // 在事务提交后执行动作。
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            // 事务提交后执行缓存操作。
            public void afterCommit() {
                action.run();
            }
        });
    }

    // 转换为 Instant。
    private Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
