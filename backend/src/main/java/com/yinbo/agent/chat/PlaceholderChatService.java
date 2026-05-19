package com.yinbo.agent.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatMessage;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.dto.ConversationDetailResponse;
import com.yinbo.agent.chat.dto.ConversationMessageResponse;
import com.yinbo.agent.chat.dto.ConversationSummaryResponse;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.mapper.ChatConversationMapper;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.AiModelProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

@Service
public class PlaceholderChatService implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderChatService.class);

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是“音波AI agent 智能助手平台”的智能助手。
            你的目标是帮助用户完成学习、编程、资料整理和任务规划。
            回答要清晰、直接、可执行。
            当用户正在学习技术时，先解决问题，再用简洁语言解释背后的知识点。
            """;

    private final AiModelProperties aiModelProperties;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;

    public PlaceholderChatService(
            AiModelProperties aiModelProperties,
            ObjectProvider<ChatModel> chatModelProvider,
            ChatConversationMapper chatConversationMapper,
            ChatMessageMapper chatMessageMapper
    ) {
        this.aiModelProperties = aiModelProperties;
        this.chatModelProvider = chatModelProvider;
        this.chatConversationMapper = chatConversationMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    @Transactional
    public ChatResponse chat(AuthUser authUser, ChatRequest request) {
        AiModelProperties.ModelOption model = aiModelProperties.findById(request.modelId());
        ChatMessage latestUserMessage = latestUserMessageOf(request);
        ChatConversation conversation = resolveConversation(authUser, request, latestUserMessage, model.id());
        ChatModel chatModel = chatModelProvider.getIfAvailable();

        persistMessage(conversation.getId(), authUser.getId(), "user", latestUserMessage.content(), model.id());

        String conversationId = conversation.getConversationNo();
        String content;
        if (chatModel == null) {
            content = fallbackResponseContent(request, model);
        } else try {
            content = callModel(chatModel, conversation.getId(), request.modelId());
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
        touchConversation(conversation, model.id(), latestUserMessage.content(), assistantMessage.getCreatedAt());
        return new ChatResponse(conversationId, model.id(), "assistant", content, toInstant(assistantMessage.getCreatedAt()));
    }

    @Override
    public List<ConversationSummaryResponse> listConversations(AuthUser authUser) {
        return chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getUserId, authUser.getId())
                        .orderByDesc(ChatConversation::getLastMessageAt)
                        .orderByDesc(ChatConversation::getCreatedAt))
                .stream()
                .map(conversation -> new ConversationSummaryResponse(
                        conversation.getConversationNo(),
                        conversation.getTitle(),
                        conversation.getModelId(),
                        toInstant(conversation.getLastMessageAt()),
                        toInstant(conversation.getCreatedAt())
                ))
                .toList();
    }

    @Override
    public ConversationDetailResponse getConversationDetail(AuthUser authUser, String conversationId) {
        ChatConversation conversation = requireOwnedConversation(authUser.getId(), conversationId);
        List<ConversationMessageResponse> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversation.getId())
                        .eq(ChatMessageEntity::getUserId, authUser.getId())
                        .orderByAsc(ChatMessageEntity::getCreatedAt)
                        .orderByAsc(ChatMessageEntity::getId))
                .stream()
                .map(message -> new ConversationMessageResponse(
                        message.getRole(),
                        message.getContent(),
                        message.getModelId(),
                        toInstant(message.getCreatedAt())
                ))
                .toList();

        return new ConversationDetailResponse(
                conversation.getConversationNo(),
                conversation.getTitle(),
                conversation.getModelId(),
                toInstant(conversation.getCreatedAt()),
                messages
        );
    }

    private String callModel(ChatModel chatModel, Long conversationId, String modelId) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(DEFAULT_SYSTEM_PROMPT));

        chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .orderByAsc(ChatMessageEntity::getCreatedAt)
                        .orderByAsc(ChatMessageEntity::getId))
                .stream()
                .map(this::toSpringAiMessage)
                .forEach(promptMessages::add);

        Prompt prompt = new Prompt(
                promptMessages,
                OpenAiChatOptions.builder()
                        .model(modelId)
                        .build()
        );

        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "模型调用成功，但没有返回可展示的内容。";
        }

        String text = response.getResult().getOutput().getText();
        return text == null || text.isBlank() ? "模型调用成功，但返回内容为空。" : text;
    }

    private Message toSpringAiMessage(ChatMessage message) {
        return toSpringAiMessage(message.role(), message.content());
    }

    private Message toSpringAiMessage(ChatMessageEntity message) {
        return toSpringAiMessage(message.getRole(), message.getContent());
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

    private String fallbackResponseContent(ChatRequest request, AiModelProperties.ModelOption model) {
        String latestUserMessage = request.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .reduce((previous, current) -> current)
                .map(ChatMessage::content)
                .orElse("");

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
