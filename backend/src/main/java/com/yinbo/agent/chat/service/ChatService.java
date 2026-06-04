package com.yinbo.agent.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatMessage;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.dto.ChatStreamEvent;
import com.yinbo.agent.chat.dto.ConversationDetailResponse;
import com.yinbo.agent.chat.dto.ConversationMessageResponse;
import com.yinbo.agent.chat.dto.ConversationSummaryResponse;
import com.yinbo.agent.chat.dto.PinConversationRequest;
import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.mapper.ChatConversationMapper;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.infra.ai.AiInfraClient;
import com.yinbo.ai.api.chat.LLMMessage;
import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.model.ModelOption;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
// AI 对话业务服务。
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final long STREAM_TIMEOUT_MILLIS = 180_000L;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是“音波AI agent 智能助手平台”的智能助手。
            你的目标是帮助用户完成学习、编程、资料整理和任务规划。
            回答要清晰、直接、可执行。
            当用户正在学习技术时，先解决问题，再用简洁语言解释背后的知识点。
            """;

    private static final String THINK_MODE_PROMPT = """

            当前启用了 Think 模式。
            你必须先输出一个可公开展示的“思考过程”摘要，再输出最终回答。
            注意：这里的“思考过程”是面向用户的推理摘要，不是完整隐藏推理链；不要使用 <think> 标签。
            必须严格使用下面的 Markdown 格式：

            **思考过程**
            - 用 2 到 5 条说明你如何拆解问题、判断关键点、选择方案。
            - 如果问题很简单，也至少给出 1 条简短判断。

            **最终回答**
            - 给出可执行、清晰的最终答案。
            """;

    private static final String NON_THINK_MODE_PROMPT = """

            当前未启用 Think 模式。
            你必须直接输出最终回答，不要输出“思考过程”“思考摘要”“最终回答”等标题。
            可以简洁解释原因和步骤，但不要把回答拆成思考过程和最终回答两个区域。
            """;

    private static final Pattern FINAL_ANSWER_HEADING_PATTERN = Pattern.compile(
            "(?s)(?:^|\\R|\\s)(?:(?:\\*\\*)?最终回答(?:\\*\\*)?|(?:\\*\\*回答\\*\\*)|回答\\s*[:：])\\s*[:：]?\\s*"
    );

    private final AiInfraClient aiInfraClient;
    private final LLMService llmService;
    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageCacheService chatMessageCacheService;

    // 注入 AI 基础设施客户端、LLM 服务、会话消息 Mapper 和缓存服务。
    public ChatService(
            AiInfraClient aiInfraClient,
            LLMService llmService,
            ChatConversationMapper chatConversationMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageCacheService chatMessageCacheService
    ) {
        this.aiInfraClient = aiInfraClient;
        this.llmService = llmService;
        this.chatConversationMapper = chatConversationMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageCacheService = chatMessageCacheService;
    }

    @Transactional
    // 执行普通非流式 AI 对话。
    public ChatResponse chat(AuthUser authUser, ChatRequest request) {
        ModelOption model = aiInfraClient.resolveModel(request.modelId());
        ChatMessage latestUserMessage = latestUserMessageOf(request);
        ChatConversation conversation = resolveConversation(authUser, request, latestUserMessage, model.id());

        List<CachedChatMessage> conversationMessages = loadConversationMessages(authUser.getId(), conversation.getId());
        ChatMessageEntity userMessage = persistMessage(
                conversation.getId(),
                authUser.getId(),
                "user",
                latestUserMessage.content(),
                model.id()
        );
        conversationMessages = appendCachedMessage(conversationMessages, userMessage);

        String conversationId = conversation.getConversationNo();
        long responseStartedAt = System.nanoTime();
        ModelCallResult modelCallResult;
        try {
            modelCallResult = callModel(conversationMessages, model.id(), request.thinkModeEnabled(), responseStartedAt);
        } catch (Exception exception) {
            log.warn(
                    "event=ai_call_failed mode=sync conversationId={} modelId={} type={} message={}",
                    conversationId,
                    model.id(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            String content = modelFailureResponseContent(model, latestUserMessage.content());
            long responseDurationMs = elapsedMillis(responseStartedAt);
            modelCallResult = new ModelCallResult(
                    model.id(),
                    content,
                    responseDurationMs,
                    null,
                    estimateTokenCount(content),
                    estimateTokenCount(latestUserMessage.content()) + estimateTokenCount(content)
            );
        }

        ChatMessageEntity assistantMessage = persistMessage(
                conversation.getId(),
                authUser.getId(),
                "assistant",
                modelCallResult.content(),
                modelCallResult.modelId(),
                modelCallResult.responseDurationMs(),
                modelCallResult.promptTokens(),
                modelCallResult.completionTokens(),
                modelCallResult.totalTokens()
        );
        conversationMessages = appendCachedMessage(conversationMessages, assistantMessage);
        touchConversation(conversation, modelCallResult.modelId(), latestUserMessage.content(), assistantMessage.getCreatedAt());
        putConversationMessagesAfterCommit(authUser.getId(), conversation.getId(), conversationMessages);
        log.info(
                "event=ai_chat_completed mode=sync userId={} conversationId={} modelId={} costMs={} promptTokens={} completionTokens={} totalTokens={}",
                authUser.getId(),
                conversationId,
                modelCallResult.modelId(),
                modelCallResult.responseDurationMs(),
                modelCallResult.promptTokens(),
                modelCallResult.completionTokens(),
                modelCallResult.totalTokens()
        );
        return new ChatResponse(
                conversationId,
                modelCallResult.modelId(),
                "assistant",
                modelCallResult.content(),
                toInstant(assistantMessage.getCreatedAt()),
                modelCallResult.responseDurationMs(),
                modelCallResult.totalTokens()
        );
    }

    // 启动 SSE 流式 AI 对话。
    public SseEmitter streamChat(AuthUser authUser, ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        CompletableFuture.runAsync(() -> {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                doStreamChat(authUser, request, emitter);
            } finally {
                MDC.clear();
            }
        });
        return emitter;
    }

    // 查询当前用户的会话列表。
    public List<ConversationSummaryResponse> listConversations(AuthUser authUser) {
        return chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getUserId, authUser.getId())
                        .last("ORDER BY pinned_at IS NULL ASC, pinned_at DESC, last_message_at DESC, created_at DESC"))
                .stream()
                .map(this::toConversationSummary)
                .toList();
    }

    // 查询当前用户拥有的会话详情。
    public ConversationDetailResponse getConversationDetail(AuthUser authUser, String conversationId) {
        ChatConversation conversation = requireOwnedConversation(authUser.getId(), conversationId);
        List<ConversationMessageResponse> messages = loadConversationMessages(authUser.getId(), conversation.getId()).stream()
                .map(this::toConversationMessageResponse)
                .toList();

        return new ConversationDetailResponse(
                conversation.getConversationNo(),
                conversation.getTitle(),
                conversation.getModelId(),
                toInstant(conversation.getCreatedAt()),
                messages
        );
    }

    @Transactional
    // 更新会话置顶状态。
    public ConversationSummaryResponse updateConversationPin(
            AuthUser authUser,
            String conversationId,
            PinConversationRequest request
    ) {
        ChatConversation conversation = requireOwnedConversation(authUser.getId(), conversationId);
        boolean pinned = request != null && request.pinnedEnabled();
        if (pinned) {
            conversation.setPinnedAt(LocalDateTime.now());
            chatConversationMapper.updateById(conversation);
        } else {
            unpinConversationById(authUser.getId(), conversation);
        }
        return toConversationSummary(conversation);
    }

    @Transactional
    // 取消指定会话置顶。
    public ConversationSummaryResponse unpinConversation(AuthUser authUser, String conversationId) {
        ChatConversation conversation = requireOwnedConversation(authUser.getId(), conversationId);
        unpinConversationById(authUser.getId(), conversation);
        return toConversationSummary(conversation);
    }

    // 按会话主键取消置顶。
    private void unpinConversationById(Long userId, ChatConversation conversation) {
        chatConversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversation.getId())
                .eq(ChatConversation::getUserId, userId)
                .set(ChatConversation::getPinnedAt, null));
        conversation.setPinnedAt(null);
    }

    @Transactional
    // 删除指定会话及其消息。
    public void deleteConversation(AuthUser authUser, String conversationId) {
        ChatConversation conversation = requireOwnedConversation(authUser.getId(), conversationId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversation.getId())
                .eq(ChatMessageEntity::getUserId, authUser.getId()));
        chatConversationMapper.deleteById(conversation.getId());
        evictConversationMessagesAfterCommit(authUser.getId(), conversation.getId());
    }

    // 转换会话列表项响应。
    private ConversationSummaryResponse toConversationSummary(ChatConversation conversation) {
        return new ConversationSummaryResponse(
                conversation.getConversationNo(),
                conversation.getTitle(),
                conversation.getModelId(),
                conversation.getPinnedAt() != null,
                conversation.getPinnedAt() == null ? null : toInstant(conversation.getPinnedAt()),
                toInstant(conversation.getLastMessageAt()),
                toInstant(conversation.getCreatedAt())
        );
    }

    // 执行流式对话主流程。
    private void doStreamChat(AuthUser authUser, ChatRequest request, SseEmitter emitter) {
        ModelOption model = aiInfraClient.resolveModel(request.modelId());
        ChatMessage latestUserMessage;
        ChatConversation conversation;
        String conversationId = null;
        try {
            latestUserMessage = latestUserMessageOf(request);
            conversation = resolveConversation(authUser, request, latestUserMessage, model.id());
            conversationId = conversation.getConversationNo();
            List<CachedChatMessage> conversationMessages = loadConversationMessages(authUser.getId(), conversation.getId());
            ChatMessageEntity userMessage = persistMessage(conversation.getId(), authUser.getId(), "user", latestUserMessage.content(), model.id());
            conversationMessages = appendCachedMessage(conversationMessages, userMessage);
            putConversationMessagesAfterCommit(authUser.getId(), conversation.getId(), conversationMessages);
            sendStreamEvent(emitter, "start", ChatStreamEvent.start(conversationId, model.id()));

            ModelCallResult modelCallResult;
            long responseStartedAt = System.nanoTime();
            StringBuilder contentBuilder = new StringBuilder();
            try {
                LLMResponse response = llmService.streamChat(
                        buildLlmRequest(conversationMessages, model.id(), request.thinkModeEnabled()),
                        delta -> {
                            if (delta == null || delta.isEmpty()) {
                                return;
                            }
                            contentBuilder.append(delta);
                            sendStreamEvent(emitter, "delta", ChatStreamEvent.delta(
                                    conversation.getConversationNo(),
                                    model.id(),
                                    delta
                            ));
                        }
                );
                String content = contentBuilder.isEmpty() ? response.content() : contentBuilder.toString();
                long responseDurationMs = elapsedMillis(responseStartedAt);
                modelCallResult = toModelCallResult(response.modelId(), content, responseDurationMs, usageFrom(response), latestUserMessage.content());
            } catch (ClientDisconnectedException exception) {
                throw exception;
            } catch (Exception exception) {
                if (!contentBuilder.isEmpty()) {
                    log.warn(
                            "event=ai_stream_interrupted_after_delta conversationId={} modelId={} type={} message={}",
                            conversationId,
                            model.id(),
                            exception.getClass().getSimpleName(),
                            sanitizeLogValue(exception.getMessage()),
                            exception
                    );
                    try {
                        sendStreamEvent(emitter, "error", ChatStreamEvent.error("流式响应中断了，请重新发起对话。"));
                    } catch (ClientDisconnectedException ignored) {
                        // 客户端已断开时不再写响应。
                    }
                    safeComplete(emitter);
                    return;
                }
                log.warn(
                        "event=ai_call_failed mode=stream conversationId={} modelId={} type={} message={}",
                        conversationId,
                        model.id(),
                        exception.getClass().getSimpleName(),
                        sanitizeLogValue(exception.getMessage()),
                        exception
                );
                String content = modelFailureResponseContent(model, latestUserMessage.content());
                sendChunkedContent(emitter, conversationId, model.id(), content);
                long responseDurationMs = elapsedMillis(responseStartedAt);
                modelCallResult = new ModelCallResult(
                        model.id(),
                        content,
                        responseDurationMs,
                        null,
                        estimateTokenCount(content),
                        estimateTokenCount(latestUserMessage.content()) + estimateTokenCount(content)
                );
            }

            if (modelCallResult.content().isBlank()) {
                String content = "模型调用成功，但返回内容为空。";
                sendStreamEvent(emitter, "delta", ChatStreamEvent.delta(conversationId, modelCallResult.modelId(), content));
                modelCallResult = modelCallResult.withContent(content);
            }
            ChatMessageEntity assistantMessage = persistMessage(
                    conversation.getId(),
                    authUser.getId(),
                    "assistant",
                    modelCallResult.content(),
                    modelCallResult.modelId(),
                    modelCallResult.responseDurationMs(),
                    modelCallResult.promptTokens(),
                    modelCallResult.completionTokens(),
                    modelCallResult.totalTokens()
            );
            conversationMessages = appendCachedMessage(conversationMessages, assistantMessage);
            touchConversation(conversation, modelCallResult.modelId(), latestUserMessage.content(), assistantMessage.getCreatedAt());
            putConversationMessagesAfterCommit(authUser.getId(), conversation.getId(), conversationMessages);
            sendStreamEvent(emitter, "done", ChatStreamEvent.done(
                    conversationId,
                    modelCallResult.modelId(),
                    toInstant(assistantMessage.getCreatedAt()),
                    modelCallResult.responseDurationMs(),
                    modelCallResult.totalTokens()
            ));
            log.info(
                    "event=ai_chat_completed mode=stream userId={} conversationId={} modelId={} costMs={} promptTokens={} completionTokens={} totalTokens={}",
                    authUser.getId(),
                    conversationId,
                    modelCallResult.modelId(),
                    modelCallResult.responseDurationMs(),
                    modelCallResult.promptTokens(),
                    modelCallResult.completionTokens(),
                    modelCallResult.totalTokens()
            );
            emitter.complete();
        } catch (ClientDisconnectedException exception) {
            log.info("event=ai_stream_disconnected conversationId={} modelId={}", conversationId, model.id());
            safeComplete(emitter);
        } catch (Exception exception) {
            log.warn(
                    "event=ai_stream_failed conversationId={} modelId={} type={} message={}",
                    conversationId,
                    model.id(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            try {
                sendStreamEvent(emitter, "error", ChatStreamEvent.error("流式响应失败了，请稍后重试。"));
            } catch (ClientDisconnectedException ignored) {
                log.info("event=ai_stream_disconnected_before_error conversationId={} modelId={}", conversationId, model.id());
            }
            safeComplete(emitter);
        }
    }

    // 按小片段发送文本内容。
    private void sendChunkedContent(SseEmitter emitter, String conversationId, String modelId, String content) {
        int chunkSize = 24;
        for (int start = 0; start < content.length(); start += chunkSize) {
            String delta = content.substring(start, Math.min(start + chunkSize, content.length()));
            sendStreamEvent(emitter, "delta", ChatStreamEvent.delta(conversationId, modelId, delta));
        }
    }

    // 发送单个 SSE 事件。
    private void sendStreamEvent(SseEmitter emitter, String eventName, ChatStreamEvent event) {
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

    // 安全结束 SSE 连接。
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            log.debug("SSE emitter already completed.");
        }
    }

    // 客户端断开连接异常。
    private static class ClientDisconnectedException extends RuntimeException {

        ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }

    // 调用同步模型接口。
    private ModelCallResult callModel(
            List<CachedChatMessage> conversationMessages,
            String modelId,
            boolean thinkMode,
            long responseStartedAt
    ) {
        LLMResponse response = llmService.chat(buildLlmRequest(conversationMessages, modelId, thinkMode));
        String text = response.content();
        String content = text == null || text.isBlank() ? "模型调用成功，但返回内容为空。" : text;
        long responseDurationMs = elapsedMillis(responseStartedAt);
        return toModelCallResult(response.modelId(), content, responseDurationMs, usageFrom(response), latestUserContent(conversationMessages));
    }

    private LLMRequest buildLlmRequest(List<CachedChatMessage> conversationMessages, String modelId, boolean thinkMode) {
        List<LLMMessage> promptMessages = new ArrayList<>();
        promptMessages.add(new LLMMessage("system", systemPrompt(thinkMode)));

        conversationMessages.stream()
                .map(message -> toLlmMessage(message, thinkMode))
                .forEach(promptMessages::add);

        return new LLMRequest(modelId, thinkMode, promptMessages);
    }

    private List<CachedChatMessage> loadConversationMessages(Long userId, Long conversationId) {
        return chatMessageCacheService.getMessages(
                userId,
                conversationId,
                () -> selectConversationMessages(userId, conversationId)
        );
    }

    private List<ChatMessageEntity> selectConversationMessages(Long userId, Long conversationId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversationId)
                .eq(ChatMessageEntity::getUserId, userId)
                .orderByAsc(ChatMessageEntity::getCreatedAt)
                .orderByAsc(ChatMessageEntity::getId));
    }

    private List<CachedChatMessage> appendCachedMessage(
            List<CachedChatMessage> conversationMessages,
            ChatMessageEntity message
    ) {
        List<CachedChatMessage> updatedMessages = new ArrayList<>(conversationMessages);
        updatedMessages.add(CachedChatMessage.from(message));
        return updatedMessages;
    }

    private void putConversationMessagesAfterCommit(
            Long userId,
            Long conversationId,
            List<CachedChatMessage> conversationMessages
    ) {
        List<CachedChatMessage> snapshot = List.copyOf(conversationMessages);
        runAfterCommit(() -> chatMessageCacheService.putMessages(userId, conversationId, snapshot));
    }

    private void evictConversationMessagesAfterCommit(Long userId, Long conversationId) {
        runAfterCommit(() -> chatMessageCacheService.evictMessages(userId, conversationId));
    }

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

    private String systemPrompt(boolean thinkMode) {
        return thinkMode ? DEFAULT_SYSTEM_PROMPT + THINK_MODE_PROMPT : DEFAULT_SYSTEM_PROMPT + NON_THINK_MODE_PROMPT;
    }

    private LLMMessage toLlmMessage(CachedChatMessage message, boolean thinkMode) {
        String content = !thinkMode && "assistant".equalsIgnoreCase(message.role())
                ? finalAnswerOnly(message.content())
                : message.content();
        return new LLMMessage(normalizeRole(message.role()), content);
    }

    private ConversationMessageResponse toConversationMessageResponse(CachedChatMessage message) {
        return new ConversationMessageResponse(
                message.role(),
                message.content(),
                message.modelId(),
                message.createdAt(),
                message.responseDurationMs(),
                message.totalTokens()
        );
    }

    private String normalizeRole(String role) {
        return switch (role.toLowerCase()) {
            case "assistant", "system", "user", "tool" -> role.toLowerCase();
            default -> "user";
        };
    }

    private String finalAnswerOnly(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        Matcher answerMatcher = FINAL_ANSWER_HEADING_PATTERN.matcher(content);
        if (!answerMatcher.find()) {
            return content;
        }
        String answer = content.substring(answerMatcher.end()).trim();
        return answer.isBlank() ? content : answer;
    }

    private String modelFailureResponseContent(ModelOption model, String latestUserMessage) {
        return """
                我已经收到你的消息：%s

                当前选择模型：%s（%s）

                这次模型调用失败了。通常是模型服务网络波动、连接被重置，或者上游暂时不可用。
                你可以先点击“新对话”重试一次；如果还是失败，稍后再试会更稳。
                """.formatted(latestUserMessage, model.name(), model.id());
    }

    private ChatConversation resolveConversation(
            AuthUser authUser,
            ChatRequest request,
            ChatMessage latestUserMessage,
            String modelId
    ) {
        if (request.conversationId() == null || request.conversationId().isBlank()) {
            ChatConversation conversation = new ChatConversation();
            conversation.setConversationNo(UUID.randomUUID().toString());
            conversation.setUserId(authUser.getId());
            conversation.setTitle(buildConversationTitle(latestUserMessage.content()));
            conversation.setModelId(modelId);
            conversation.setLastMessageAt(LocalDateTime.now());
            chatConversationMapper.insert(conversation);
            return conversation;
        }
        return requireOwnedConversation(authUser.getId(), request.conversationId());
    }

    private ChatConversation requireOwnedConversation(Long userId, String conversationId) {
        ChatConversation conversation = chatConversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationNo, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .last("LIMIT 1"));
        if (conversation == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在，或者你没有权限访问它");
        }
        return conversation;
    }

    private ChatMessage latestUserMessageOf(ChatRequest request) {
        return request.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .reduce((previous, current) -> current)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "消息列表里至少要有一条用户消息"));
    }

    private ChatMessageEntity persistMessage(
            Long conversationId,
            Long userId,
            String role,
            String content,
            String modelId
    ) {
        return persistMessage(conversationId, userId, role, content, modelId, null, null, null, null);
    }

    private ChatMessageEntity persistMessage(
            Long conversationId,
            Long userId,
            String role,
            String content,
            String modelId,
            Long responseDurationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
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
        chatMessageMapper.insert(messageEntity);
        return messageEntity;
    }

    private ModelCallResult toModelCallResult(String modelId, String content, long responseDurationMs, TokenUsage tokenUsage, String latestUserContent) {
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
        return new ModelCallResult(modelId, content, responseDurationMs, promptTokens, completionTokens, totalTokens);
    }

    private TokenUsage usageFrom(LLMResponse response) {
        if (response == null || response.usage() == null) {
            return null;
        }
        LLMResponse.TokenUsage usage = response.usage();
        return new TokenUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    }

    private String latestUserContent(List<CachedChatMessage> conversationMessages) {
        for (int index = conversationMessages.size() - 1; index >= 0; index--) {
            CachedChatMessage message = conversationMessages.get(index);
            if ("user".equalsIgnoreCase(message.role())) {
                return message.content();
            }
        }
        return "";
    }

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

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    private record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }

    private record ModelCallResult(
            String modelId,
            String content,
            Long responseDurationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {

        private ModelCallResult withContent(String nextContent) {
            return new ModelCallResult(modelId, nextContent, responseDurationMs, promptTokens, completionTokens, totalTokens);
        }
    }

    private void touchConversation(
            ChatConversation conversation,
            String modelId,
            String latestUserContent,
            LocalDateTime lastMessageAt
    ) {
        conversation.setModelId(modelId);
        conversation.setLastMessageAt(lastMessageAt);
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(buildConversationTitle(latestUserContent));
        }
        chatConversationMapper.updateById(conversation);
    }

    private String buildConversationTitle(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "新的对话";
        }
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30) + "...";
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
