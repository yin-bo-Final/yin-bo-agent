package com.yinbo.ai.infra.chat;

import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.chat.StreamCallback;
import com.yinbo.ai.infra.model.ModelTarget;

// Chat 供应商客户端。
public interface ChatClient {

    // 返回供应商标识。
    String provider();

    // 发起非流式 Chat 调用。
    LLMResponse chat(LLMRequest request, ModelTarget target);

    // 发起流式 Chat 调用。
    LLMResponse streamChat(LLMRequest request, StreamCallback callback, ModelTarget target);
}
