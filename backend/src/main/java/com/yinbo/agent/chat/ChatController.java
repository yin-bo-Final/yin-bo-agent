package com.yinbo.agent.chat;

import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.config.AiModelProperties;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final AiModelProperties aiModelProperties;

    public ChatController(ChatService chatService, AiModelProperties aiModelProperties) {
        this.chatService = chatService;
        this.aiModelProperties = aiModelProperties;
    }

    @GetMapping("/models")
    public List<AiModelProperties.ModelOption> models() {
        return aiModelProperties.models();
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }
}
