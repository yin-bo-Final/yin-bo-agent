package com.yinbo.agent.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatAssistantTraceResponse;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.dto.ConversationDetailResponse;
import com.yinbo.agent.chat.dto.ConversationMemoryCompressionResponse;
import com.yinbo.agent.chat.dto.ConversationMemorySummaryResponse;
import com.yinbo.agent.chat.dto.ConversationMessageResponse;
import com.yinbo.agent.chat.dto.ConversationSummaryResponse;
import com.yinbo.agent.chat.dto.PinConversationRequest;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.flow.ConversationFlowExecutor;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.memory.ConversationMemoryCompressionService;
import com.yinbo.agent.chat.flow.memory.ConversationMemoryService;
import com.yinbo.agent.chat.mapper.ChatConversationMapper;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import com.yinbo.agent.common.BusinessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
// AI 对话入口和会话管理服务。
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationFlowExecutor conversationFlowExecutor;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationMemoryCompressionService conversationMemoryCompressionService;
    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageCacheService chatMessageCacheService;
    private final ObjectMapper objectMapper;

    // 注入会话流水线执行器、会话 Mapper、消息 Mapper 和缓存服务。
    public ChatService(
            ConversationFlowExecutor conversationFlowExecutor,
            ConversationMemoryService conversationMemoryService,
            ConversationMemoryCompressionService conversationMemoryCompressionService,
            ChatConversationMapper chatConversationMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageCacheService chatMessageCacheService,
            ObjectMapper objectMapper
    ) {
        this.conversationFlowExecutor = conversationFlowExecutor;
        this.conversationMemoryService = conversationMemoryService;
        this.conversationMemoryCompressionService = conversationMemoryCompressionService;
        this.chatConversationMapper = chatConversationMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageCacheService = chatMessageCacheService;
        this.objectMapper = objectMapper;
    }

    // 执行普通非流式 AI 对话。
    public ChatResponse chat(AuthUser authUser, ChatRequest request) {
        return conversationFlowExecutor.executeSync(ChatExecutionContext.sync(authUser, request));
    }

    // 启动 SSE 流式 AI 对话。
    public SseEmitter streamChat(AuthUser authUser, ChatRequest request) {
        return conversationFlowExecutor.executeStream(ChatExecutionContext.stream(authUser, request));
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
                messages,
                ConversationMemorySummaryResponse.from(conversationMemoryCompressionService.selectActiveSummary(
                        authUser.getId(),
                        conversation.getId()
                ))
        );
    }

    // 手动压缩指定会话的历史记忆。
    public ConversationMemoryCompressionResponse compressConversationMemory(AuthUser authUser, String conversationId) {
        ChatConversation conversation = requireOwnedConversation(authUser.getId(), conversationId);
        List<CachedChatMessage> messages = loadConversationMessages(authUser.getId(), conversation.getId());
        return conversationMemoryCompressionService.compressManually(authUser, conversation, messages);
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
        conversationMemoryCompressionService.deleteSummaries(authUser.getId(), conversation.getId());
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

    // 查询当前用户拥有的会话。
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

    // 从缓存或数据库加载会话消息。
    private List<CachedChatMessage> loadConversationMessages(Long userId, Long conversationId) {
        return conversationMemoryService.load(userId, conversationId);
    }

    // 转换会话消息响应。
    private ConversationMessageResponse toConversationMessageResponse(CachedChatMessage message) {
        return new ConversationMessageResponse(
                message.role(),
                message.content(),
                message.modelId(),
                message.createdAt(),
                message.responseDurationMs(),
                message.totalTokens(),
                message.id(),
                parseAssistantTrace(message.assistantTraceJson())
        );
    }

    private ChatAssistantTraceResponse parseAssistantTrace(String assistantTraceJson) {
        if (assistantTraceJson == null || assistantTraceJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(assistantTraceJson, ChatAssistantTraceResponse.class);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "event=assistant_trace_decode_failed type={} message={}",
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
            return null;
        }
    }

    // 事务提交后删除会话消息缓存。
    private void evictConversationMessagesAfterCommit(Long userId, Long conversationId) {
        runAfterCommit(() -> chatMessageCacheService.evictMessages(userId, conversationId));
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
