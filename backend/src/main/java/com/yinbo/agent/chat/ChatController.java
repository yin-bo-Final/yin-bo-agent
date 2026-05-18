package com.yinbo.agent.chat;

import com.yinbo.agent.auth.AuthService;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.dto.ConversationDetailResponse;
import com.yinbo.agent.chat.dto.ConversationSummaryResponse;
import com.yinbo.agent.config.AiModelProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final AiModelProperties aiModelProperties;
    private final AuthService authService;

    public ChatController(ChatService chatService, AiModelProperties aiModelProperties, AuthService authService) {
        this.chatService = chatService;
        this.aiModelProperties = aiModelProperties;
        this.authService = authService;
    }

    @GetMapping("/models")
    public List<AiModelProperties.ModelOption> models() {
        return aiModelProperties.models();
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.chat(authUser, request);
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryResponse> conversations(HttpServletRequest httpRequest) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.listConversations(authUser);
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationDetailResponse conversationDetail(
            @PathVariable String conversationId,
            HttpServletRequest httpRequest
    ) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.getConversationDetail(authUser, conversationId);
    }
}
