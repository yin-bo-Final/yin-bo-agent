package com.yinbo.agent.chat;

import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.dto.ConversationDetailResponse;
import com.yinbo.agent.chat.dto.ConversationSummaryResponse;
import java.util.List;

public interface ChatService {

    ChatResponse chat(AuthUser authUser, ChatRequest request);

    List<ConversationSummaryResponse> listConversations(AuthUser authUser);

    ConversationDetailResponse getConversationDetail(AuthUser authUser, String conversationId);
}
