package com.yinbo.ai.api.chat;

// LLM 对话业务接口。
public interface LLMService {

    // 执行非流式对话。
    LLMResponse chat(LLMRequest request);

    // 执行流式对话，方法返回时代表流式输出结束。
    LLMResponse streamChat(LLMRequest request, StreamCallback callback);
}
