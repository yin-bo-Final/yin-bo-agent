package com.yinbo.agent.chat.flow.message;

import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
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

    // 注入消息 Mapper、缓存服务和会话生命周期服务。
    public ChatMessagePersistenceService(
            ChatMessageMapper chatMessageMapper,
            ChatMessageCacheService chatMessageCacheService,
            ConversationLifecycleService conversationLifecycleService
    ) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageCacheService = chatMessageCacheService;
        this.conversationLifecycleService = conversationLifecycleService;
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
        ChatMessageEntity assistantMessage = persistMessage(
                ctx.conversation().getId(),
                ctx.authUser().getId(),
                "assistant",
                result.content(),
                result.modelId(),
                result.responseDurationMs(),
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens()
        );
        ctx.setConversationMessages(appendCachedMessage(ctx.conversationMessages(), assistantMessage));
        conversationLifecycleService.touch(ctx, result.modelId(), assistantMessage.getCreatedAt());
        putConversationMessagesAfterCommit(ctx.authUser().getId(), ctx.conversation().getId(), ctx.conversationMessages());
        log.info(
                "event=ai_chat_completed mode={} userId={} conversationId={} modelId={} costMs={} promptTokens={} completionTokens={} totalTokens={}",
                mode,
                ctx.authUser().getId(),
                ctx.conversation().getConversationNo(),
                result.modelId(),
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
                result.responseDurationMs(),
                result.totalTokens()
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
        return persistMessage(conversationId, userId, role, content, modelId, null, null, null, null);
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
}
