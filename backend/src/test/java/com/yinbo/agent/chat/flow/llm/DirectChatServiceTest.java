package com.yinbo.agent.chat.flow.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatMessage;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.message.AssistantResponseResult;
import com.yinbo.agent.chat.flow.prompt.PromptAssemblyService;
import com.yinbo.agent.chat.flow.response.ChatStreamResponseWriter;
import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.chat.StreamCallback;
import com.yinbo.ai.api.model.ModelOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class DirectChatServiceTest {

    private final LLMService llmService = mock(LLMService.class);
    private final PromptAssemblyService promptAssemblyService = mock(PromptAssemblyService.class);
    private final ChatStreamResponseWriter streamResponseWriter = mock(ChatStreamResponseWriter.class);
    private final DirectChatService directChatService = new DirectChatService(
            llmService,
            promptAssemblyService,
            streamResponseWriter
    );

    @Test
    void streamSendsReturnedContentWhenProviderDoesNotEmitDelta() {
        ChatExecutionContext ctx = streamContext();
        LLMRequest request = new LLMRequest("qwen", false, List.of());
        when(promptAssemblyService.buildDirectRequest(ctx)).thenReturn(request);
        when(llmService.streamChat(eq(request), any(StreamCallback.class)))
                .thenReturn(new LLMResponse("qwen", "完整流式响应", null));

        AssistantResponseResult result = directChatService.stream(ctx);

        assertThat(result.content()).isEqualTo("完整流式响应");
        verify(streamResponseWriter).sendChunkedContent(ctx, "完整流式响应");
        verify(streamResponseWriter, never()).sendDelta(ArgumentMatchers.any(), ArgumentMatchers.anyString());
    }

    @Test
    void streamDoesNotReplayContentWhenProviderAlreadyEmitsDeltas() {
        ChatExecutionContext ctx = streamContext();
        LLMRequest request = new LLMRequest("qwen", false, List.of());
        when(promptAssemblyService.buildDirectRequest(ctx)).thenReturn(request);
        when(llmService.streamChat(eq(request), any(StreamCallback.class))).thenAnswer(invocation -> {
            StreamCallback callback = invocation.getArgument(1);
            callback.onDelta("你");
            callback.onDelta("好");
            return new LLMResponse("qwen", "你好", null);
        });

        AssistantResponseResult result = directChatService.stream(ctx);

        assertThat(result.content()).isEqualTo("你好");
        verify(streamResponseWriter).sendDelta(ctx, "你");
        verify(streamResponseWriter).sendDelta(ctx, "好");
        verify(streamResponseWriter, never()).sendChunkedContent(ArgumentMatchers.any(), ArgumentMatchers.anyString());
    }

    @Test
    void streamSendsEmptyContentNoticeWhenProviderReturnsBlank() {
        ChatExecutionContext ctx = streamContext();
        LLMRequest request = new LLMRequest("qwen", false, List.of());
        when(promptAssemblyService.buildDirectRequest(ctx)).thenReturn(request);
        when(llmService.streamChat(eq(request), any(StreamCallback.class)))
                .thenReturn(new LLMResponse("qwen", "  ", null));

        AssistantResponseResult result = directChatService.stream(ctx);

        assertThat(result.content()).isEqualTo("模型调用成功，但返回内容为空。");
        verify(streamResponseWriter).sendChunkedContent(ctx, "模型调用成功，但返回内容为空。");
    }

    private ChatExecutionContext streamContext() {
        ChatRequest request = new ChatRequest(
                null,
                "qwen",
                List.of(new ChatMessage("user", "你好")),
                false
        );
        ChatExecutionContext ctx = ChatExecutionContext.stream(new AuthUser(), request);
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationNo("conv-1");
        ctx.setConversation(conversation);
        ctx.setModel(new ModelOption("qwen", "Qwen", "test", true));
        ctx.setLatestUserMessage(new ChatMessage("user", "你好"));
        return ctx;
    }
}
