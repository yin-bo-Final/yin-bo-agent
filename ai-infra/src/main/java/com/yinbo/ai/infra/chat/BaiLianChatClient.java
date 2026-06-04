package com.yinbo.ai.infra.chat;

import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.chat.StreamCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
// 百炼 OpenAI 兼容 Chat 客户端。
public class BaiLianChatClient extends AbstractOpenAiStyleChatClient {

    public BaiLianChatClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String provider() {
        return "bailian";
    }
}
