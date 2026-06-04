package com.yinbo.ai.infra.chat;

import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.chat.StreamCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
// Ollama OpenAI 兼容 Chat 客户端。
public class OllamaChatClient extends AbstractOpenAiStyleChatClient {

    public OllamaChatClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String provider() {
        return "ollama";
    }
}
