package com.yinbo.agent.chat.flow.lifecycle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatMessage;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.mapper.ChatConversationMapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.infra.ai.AiInfraClient;
import com.yinbo.ai.api.model.ModelOption;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
// 会话生命周期服务，负责会话创建、恢复和最近消息信息维护。
public class ConversationLifecycleService {

    private final AiInfraClient aiInfraClient;
    private final ChatConversationMapper chatConversationMapper;

    // 注入模型解析客户端和会话 Mapper。
    public ConversationLifecycleService(
            AiInfraClient aiInfraClient,
            ChatConversationMapper chatConversationMapper
    ) {
        this.aiInfraClient = aiInfraClient;
        this.chatConversationMapper = chatConversationMapper;
    }

    // 准备本轮会话、模型和最新用户消息。
    public void prepare(ChatExecutionContext ctx) {
        ModelOption model = aiInfraClient.resolveModel(ctx.request().modelId());
        ChatMessage latestUserMessage = latestUserMessageOf(ctx.request());
        ChatConversation conversation = resolveConversation(ctx.authUser(), ctx.request(), latestUserMessage, model.id());
        ctx.setModel(model);
        ctx.setLatestUserMessage(latestUserMessage);
        ctx.setConversation(conversation);
    }

    // 更新会话最近消息信息。
    public void touch(ChatExecutionContext ctx, String modelId, LocalDateTime lastMessageAt) {
        ChatConversation conversation = ctx.conversation();
        conversation.setModelId(modelId);
        conversation.setLastMessageAt(lastMessageAt);
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(buildConversationTitle(ctx.latestUserMessage().content()));
        }
        chatConversationMapper.updateById(conversation);
    }

    // 创建或恢复当前会话。
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

    // 取请求中的最后一条用户消息。
    private ChatMessage latestUserMessageOf(ChatRequest request) {
        return request.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .reduce((previous, current) -> current)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "消息列表里至少要有一条用户消息"));
    }

    // 根据用户首条消息构建会话标题。
    private String buildConversationTitle(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "新的对话";
        }
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30) + "...";
    }
}
