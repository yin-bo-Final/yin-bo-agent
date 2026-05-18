package com.yinbo.agent.chat;

import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;

public interface ChatService {

    ChatResponse chat(ChatRequest request);
}
