package com.yinbo.agent.chat;

import com.yinbo.agent.chat.dto.ChatMessage;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.config.AiModelProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PlaceholderChatService implements ChatService {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是“音波AI agent 智能助手平台”的智能助手。
            你的目标是帮助用户完成学习、编程、资料整理和任务规划。
            回答要清晰、直接、可执行。
            当用户正在学习技术时，先解决问题，再用简洁语言解释背后的知识点。
            """;

    private final AiModelProperties aiModelProperties;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public PlaceholderChatService(AiModelProperties aiModelProperties, ObjectProvider<ChatModel> chatModelProvider) {
        this.aiModelProperties = aiModelProperties;
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        AiModelProperties.ModelOption model = aiModelProperties.findById(request.modelId());
        ChatModel chatModel = chatModelProvider.getIfAvailable();

        if (chatModel == null) {
            return fallbackResponse(request, model);
        }

        String conversationId = conversationIdOf(request);
        String content = callModel(chatModel, request);
        return new ChatResponse(conversationId, model.id(), "assistant", content, Instant.now());
    }

    private String callModel(ChatModel chatModel, ChatRequest request) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(DEFAULT_SYSTEM_PROMPT));

        for (ChatMessage message : request.messages()) {
            promptMessages.add(toSpringAiMessage(message));
        }

        Prompt prompt = new Prompt(
                promptMessages,
                OpenAiChatOptions.builder()
                        .model(request.modelId())
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
        return switch (message.role().toLowerCase()) {
            case "assistant" -> new AssistantMessage(message.content());
            case "system" -> new SystemMessage(message.content());
            case "user" -> new UserMessage(message.content());
            default -> new UserMessage(message.content());
        };
    }

    private ChatResponse fallbackResponse(ChatRequest request, AiModelProperties.ModelOption model) {
        String latestUserMessage = request.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .reduce((previous, current) -> current)
                .map(ChatMessage::content)
                .orElse("");

        String conversationId = conversationIdOf(request);

        String content = """
                我已经收到你的消息：%s

                当前选择模型：%s（%s）

                当前没有检测到可用的模型客户端。请先在项目根目录配置 `local-secrets.yml`，填入硅基流动 API Key 和中间件密码，然后重新启动后端。
                """.formatted(latestUserMessage, model.name(), model.id());

        return new ChatResponse(conversationId, model.id(), "assistant", content, Instant.now());
    }

    private String conversationIdOf(ChatRequest request) {
        return request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();
    }
}
