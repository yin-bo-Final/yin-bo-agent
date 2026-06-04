package com.yinbo.agent.chat.controller;

import com.yinbo.agent.auth.service.AuthService;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.dto.ConversationDetailResponse;
import com.yinbo.agent.chat.dto.ConversationSummaryResponse;
import com.yinbo.agent.chat.dto.PinConversationRequest;
import com.yinbo.agent.chat.service.ChatService;
import com.yinbo.agent.infra.ai.AiInfraClient;
import com.yinbo.ai.api.model.ModelOption;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
// AI 对话和会话管理接口。
public class ChatController {

    private final ChatService chatService;
    private final AiInfraClient aiInfraClient;
    private final AuthService authService;

    // 注入对话服务、AI 基础设施客户端和认证服务。
    public ChatController(ChatService chatService, AiInfraClient aiInfraClient, AuthService authService) {
        this.chatService = chatService;
        this.aiInfraClient = aiInfraClient;
        this.authService = authService;
    }

    @GetMapping("/models")
    // 查询前端可选择的 AI 模型列表。
    public List<ModelOption> models() {
        return aiInfraClient.models();
    }

    @PostMapping("/chat")
    // 发起普通非流式 AI 对话。
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.chat(authUser, request);
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // 发起 SSE 流式 AI 对话。
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.streamChat(authUser, request);
    }

    @GetMapping("/conversations")
    // 查询当前用户的会话列表。
    public List<ConversationSummaryResponse> conversations(HttpServletRequest httpRequest) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.listConversations(authUser);
    }

    @GetMapping("/conversations/{conversationId}")
    // 查询指定会话详情和消息列表。
    public ConversationDetailResponse conversationDetail(
            @PathVariable String conversationId,
            HttpServletRequest httpRequest
    ) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.getConversationDetail(authUser, conversationId);
    }

    @RequestMapping(path = "/conversations/{conversationId}/pin", method = {
            org.springframework.web.bind.annotation.RequestMethod.POST,
            org.springframework.web.bind.annotation.RequestMethod.PATCH
    })
    // 更新会话置顶状态。
    public ConversationSummaryResponse updateConversationPin(
            @PathVariable String conversationId,
            @RequestBody PinConversationRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.updateConversationPin(authUser, conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/unpin")
    // 取消会话置顶。
    public ConversationSummaryResponse unpinConversation(
            @PathVariable String conversationId,
            HttpServletRequest httpRequest
    ) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        return chatService.unpinConversation(authUser, conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    // 删除指定会话及其消息。
    public void deleteConversation(@PathVariable String conversationId, HttpServletRequest httpRequest) {
        AuthUser authUser = authService.requireActiveUser(httpRequest);
        chatService.deleteConversation(authUser, conversationId);
    }
}
