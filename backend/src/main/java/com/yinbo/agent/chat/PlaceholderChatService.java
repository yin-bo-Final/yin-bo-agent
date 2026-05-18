package com.yinbo.agent.chat;

import com.yinbo.agent.chat.dto.ChatMessage;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.config.AiModelProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlaceholderChatService implements ChatService {

    private final AiModelProperties aiModelProperties;

    public PlaceholderChatService(AiModelProperties aiModelProperties) {
        this.aiModelProperties = aiModelProperties;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        AiModelProperties.ModelOption model = aiModelProperties.findById(request.modelId());
        String latestUserMessage = request.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .reduce((previous, current) -> current)
                .map(ChatMessage::content)
                .orElse("");

        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();

        String content = """
                我已经收到你的消息：%s

                当前选择模型：%s（%s）

                真实 LLM API 还没有接入。等你确定 API key、模型供应商和具体模型后，我们会把这里替换成 Spring AI 的真实 ChatModel 调用，并保留前端这套对话界面。
                """.formatted(latestUserMessage, model.name(), model.id());

        return new ChatResponse(conversationId, model.id(), "assistant", content, Instant.now());
    }
}
