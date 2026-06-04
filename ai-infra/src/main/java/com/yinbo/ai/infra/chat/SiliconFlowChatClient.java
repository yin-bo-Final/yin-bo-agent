package com.yinbo.ai.infra.chat;

import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.chat.StreamCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
// 硅基流动 Chat 客户端。
public class SiliconFlowChatClient extends AbstractOpenAiStyleChatClient {

    public SiliconFlowChatClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String provider() {
        return "siliconflow";
    }
}
