package com.yinbo.agent.chat;

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
import com.yinbo.agent.chat.ChatMessageCacheService.CachedChatMessage;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.mapper.ChatConversationMapper;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.AiModelProperties;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
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

    private final AiModelProperties aiModelProperties;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageCacheService chatMessageCacheService;

    public ChatService(
            AiModelProperties aiModelProperties,
            ObjectProvider<ChatModel> chatModelProvider,
            ChatConversationMapper chatConversationMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageCacheService chatMessageCacheService
    ) {
        this.aiModelProperties = aiModelProperties;
        this.chatModelProvider = chatModelProvider;
        this.chatConversationMapper = chatConversationMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageCacheService = chatMessageCacheService;
    }

    @Transactional
    public ChatResponse chat(AuthUser authUser, ChatRequest request) {
        AiModelProperties.ModelOption model = aiModelProperties.findById(request.modelId());
        ChatMessage latestUserMessage = latestUserMessageOf(request);
        ChatConversation conversation = resolveConversation(authUser, request, latestUserMessage, model.id());
        ChatModel chatModel = chatModelProvider.getIfAvailable();

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
        String content;
        if (chatModel == null) {
            content = fallbackResponseContent(latestUserMessage.content(), model);
        } else try {
            content = callModel(chatModel, conversationMessages, request.modelId(), request.thinkModeEnabled());
        } catch (Exception exception) {
            log.warn("Chat model call failed. conversationId={}, modelId={}", conversationId, model.id(), exception);
            content = modelFailureResponseContent(model, latestUserMessage.content());
        }

        ChatMessageEntity assistantMessage = persistMessage(
                conversation.getId(),
                authUser.getId(),
                "assistant",
                content,
                model.id()
        );
        conversationMessages = appendCachedMessage(conversationMessages, assistantMessage);
        touchConversation(conversation, model.id(), latestUserMessage.content(), assistantMessage.getCreatedAt());
        putConversationMessagesAfterCommit(authUser.getId(), conversation.getId(), conversationMessages);
        return new ChatResponse(conversationId, model.id(), "assistant", content, toInstant(assistantMessage.getCreatedAt()));
    }

    public SseEmitter streamChat(AuthUser authUser, ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        CompletableFuture.runAsync(() -> doStreamChat(authUser, request, emitter));
        return emitter;
    }

    public List<ConversationSummaryResponse> listConversations(AuthUser authUser) {
        return chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getUserId, authUser.getId())
                        .last("ORDER BY pinned_at IS NULL ASC, pinned_at DESC, last_message_at DESC, created_at DESC"))
                .stream()
                .map(this::toConversationSummary)
                .toList();
    }

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
    public ConversationSummaryResponse unpinConversation(AuthUser authUser, String conversationId) {
        ChatConversation conversation = requireOwnedConversation(authUser.getId(), conversationId);
        unpinConversationById(authUser.getId(), conversation);
        return toConversationSummary(conversation);
    }

    private void unpinConversationById(Long userId, ChatConversation conversation) {
        chatConversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversation.getId())
                .eq(ChatConversation::getUserId, userId)
                .set(ChatConversation::getPinnedAt, null));
        conversation.setPinnedAt(null);
    }

    @Transactional
    public void deleteConversation(AuthUser authUser, String conversationId) {
        ChatConversation conversation = requireOwnedConversation(authUser.getId(), conversationId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversation.getId())
                .eq(ChatMessageEntity::getUserId, authUser.getId()));
        chatConversationMapper.deleteById(conversation.getId());
        evictConversationMessagesAfterCommit(authUser.getId(), conversation.getId());
    }

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

    private void doStreamChat(AuthUser authUser, ChatRequest request, SseEmitter emitter) {
        AiModelProperties.ModelOption model = aiModelProperties.findById(request.modelId());
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

            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                streamFallbackContent(emitter, conversation, authUser, model, latestUserMessage, conversationMessages);
                return;
            }

            String content;
            try {
                StringBuilder contentBuilder = new StringBuilder();
                Prompt prompt = buildPrompt(conversationMessages, model.id(), request.thinkModeEnabled());
                chatModel.stream(prompt)
                        .toIterable()
                        .forEach(response -> {
                            String delta = responseContent(response);
                            if (delta == null || delta.isEmpty()) {
                                return;
                            }
                            contentBuilder.append(delta);
                            sendStreamEvent(emitter, "delta", ChatStreamEvent.delta(
                                    conversation.getConversationNo(),
                                    model.id(),
                                    delta
                            ));
                        });
                content = contentBuilder.toString();
            } catch (ClientDisconnectedException exception) {
                throw exception;
            } catch (Exception exception) {
                log.warn("Streaming chat model call failed. conversationId={}, modelId={}", conversationId, model.id(), exception);
                content = modelFailureResponseContent(model, latestUserMessage.content());
                sendChunkedContent(emitter, conversationId, model.id(), content);
            }

            if (content.isBlank()) {
                content = "模型调用成功，但返回内容为空。";
                sendStreamEvent(emitter, "delta", ChatStreamEvent.delta(conversationId, model.id(), content));
            }
            ChatMessageEntity assistantMessage = persistMessage(
                    conversation.getId(),
                    authUser.getId(),
                    "assistant",
                    content,
                    model.id()
            );
            conversationMessages = appendCachedMessage(conversationMessages, assistantMessage);
            touchConversation(conversation, model.id(), latestUserMessage.content(), assistantMessage.getCreatedAt());
            putConversationMessagesAfterCommit(authUser.getId(), conversation.getId(), conversationMessages);
            sendStreamEvent(emitter, "done", ChatStreamEvent.done(
                    conversationId,
                    model.id(),
                    toInstant(assistantMessage.getCreatedAt())
            ));
            emitter.complete();
        } catch (ClientDisconnectedException exception) {
            log.debug("Streaming chat client disconnected. conversationId={}, modelId={}", conversationId, model.id());
            safeComplete(emitter);
        } catch (Exception exception) {
            log.warn("Streaming chat failed. conversationId={}, modelId={}", conversationId, model.id(), exception);
            try {
                sendStreamEvent(emitter, "error", ChatStreamEvent.error("流式响应失败了，请稍后重试。"));
            } catch (ClientDisconnectedException ignored) {
                log.debug("Streaming chat client disconnected before error event. conversationId={}, modelId={}", conversationId, model.id());
            }
            safeComplete(emitter);
        }
    }

    private void streamFallbackContent(
            SseEmitter emitter,
            ChatConversation conversation,
            AuthUser authUser,
            AiModelProperties.ModelOption model,
            ChatMessage latestUserMessage,
            List<CachedChatMessage> conversationMessages
    ) {
        String content = fallbackResponseContent(latestUserMessage.content(), model);
        sendChunkedContent(emitter, conversation.getConversationNo(), model.id(), content);
        ChatMessageEntity assistantMessage = persistMessage(
                conversation.getId(),
                authUser.getId(),
                "assistant",
                content,
                model.id()
        );
        conversationMessages = appendCachedMessage(conversationMessages, assistantMessage);
        touchConversation(conversation, model.id(), latestUserMessage.content(), assistantMessage.getCreatedAt());
        putConversationMessagesAfterCommit(authUser.getId(), conversation.getId(), conversationMessages);
        sendStreamEvent(emitter, "done", ChatStreamEvent.done(
                conversation.getConversationNo(),
                model.id(),
                toInstant(assistantMessage.getCreatedAt())
        ));
        emitter.complete();
    }

    private void sendChunkedContent(SseEmitter emitter, String conversationId, String modelId, String content) {
        int chunkSize = 24;
        for (int start = 0; start < content.length(); start += chunkSize) {
            String delta = content.substring(start, Math.min(start + chunkSize, content.length()));
            sendStreamEvent(emitter, "delta", ChatStreamEvent.delta(conversationId, modelId, delta));
        }
    }

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

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            log.debug("SSE emitter already completed.");
        }
    }

    private static class ClientDisconnectedException extends RuntimeException {

        ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }

    private String callModel(
            ChatModel chatModel,
            List<CachedChatMessage> conversationMessages,
            String modelId,
            boolean thinkMode
    ) {
        Prompt prompt = buildPrompt(conversationMessages, modelId, thinkMode);
        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
        String text = responseContent(response);
        return text == null || text.isBlank() ? "模型调用成功，但返回内容为空。" : text;
    }

    private Prompt buildPrompt(List<CachedChatMessage> conversationMessages, String modelId, boolean thinkMode) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemPrompt(thinkMode)));

        conversationMessages.stream()
                .map(message -> toSpringAiMessage(message, thinkMode))
                .forEach(promptMessages::add);

        return new Prompt(
                promptMessages,
                OpenAiChatOptions.builder()
                        .model(modelId)
                        .build()
        );
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
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String responseContent(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }

        return response.getResult().getOutput().getText();
    }

    private String systemPrompt(boolean thinkMode) {
        return thinkMode ? DEFAULT_SYSTEM_PROMPT + THINK_MODE_PROMPT : DEFAULT_SYSTEM_PROMPT + NON_THINK_MODE_PROMPT;
    }

    private Message toSpringAiMessage(CachedChatMessage message, boolean thinkMode) {
        String content = !thinkMode && "assistant".equalsIgnoreCase(message.role())
                ? finalAnswerOnly(message.content())
                : message.content();
        return toSpringAiMessage(message.role(), content);
    }

    private ConversationMessageResponse toConversationMessageResponse(CachedChatMessage message) {
        return new ConversationMessageResponse(
                message.role(),
                message.content(),
                message.modelId(),
                message.createdAt()
        );
    }

    private Message toSpringAiMessage(String role, String content) {
        return switch (role.toLowerCase()) {
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            case "user" -> new UserMessage(content);
            case "tool" -> new AssistantMessage(content);
            default -> new UserMessage(content);
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

    private String fallbackResponseContent(String latestUserMessage, AiModelProperties.ModelOption model) {
        return """
                我已经收到你的消息：%s

                当前选择模型：%s（%s）

                当前没有检测到可用的模型客户端。请先在项目根目录配置 `local-secrets.yml`，填入硅基流动 API Key 和中间件密码，然后重新启动后端。
                """.formatted(latestUserMessage, model.name(), model.id());
    }

    private String modelFailureResponseContent(AiModelProperties.ModelOption model, String latestUserMessage) {
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
        ChatMessageEntity messageEntity = new ChatMessageEntity();
        messageEntity.setConversationId(conversationId);
        messageEntity.setUserId(userId);
        messageEntity.setRole(role);
        messageEntity.setContent(content);
        messageEntity.setModelId(modelId);
        chatMessageMapper.insert(messageEntity);
        return messageEntity;
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
