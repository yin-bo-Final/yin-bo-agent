package com.yinbo.agent.chat;

import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.dto.ConversationDetailResponse;
import com.yinbo.agent.chat.dto.ConversationSummaryResponse;
import com.yinbo.agent.chat.dto.PinConversationRequest;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    ChatResponse chat(AuthUser authUser, ChatRequest request);

    SseEmitter streamChat(AuthUser authUser, ChatRequest request);

    List<ConversationSummaryResponse> listConversations(AuthUser authUser);

    ConversationDetailResponse getConversationDetail(AuthUser authUser, String conversationId);

    ConversationSummaryResponse updateConversationPin(
            AuthUser authUser,
            String conversationId,
            PinConversationRequest request
    );

    ConversationSummaryResponse unpinConversation(AuthUser authUser, String conversationId);

    void deleteConversation(AuthUser authUser, String conversationId);
}
