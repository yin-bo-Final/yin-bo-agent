package com.yinbo.agent.chat.flow.context;

import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ChatMessage;
import com.yinbo.agent.chat.dto.ChatRequest;
import com.yinbo.agent.chat.dto.ChatResponse;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.entity.ConversationMemorySummary;
import com.yinbo.agent.chat.flow.query.QueryRewriteResult;
import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import com.yinbo.ai.api.model.ModelOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 单次会话执行过程中跨阶段传递的上下文。
public class ChatExecutionContext {

    private final AuthUser authUser;
    private final ChatRequest request;
    private final boolean streamMode;
    private SseEmitter emitter;
    private ModelOption model;
    private ChatMessage latestUserMessage;
    private ChatConversation conversation;
    private List<CachedChatMessage> conversationMessages = new ArrayList<>();
    private List<CachedChatMessage> promptConversationMessages = new ArrayList<>();
    private ConversationMemorySummary memorySummary;
    private ChatMessageEntity userMessage;
    private String originalQuery;
    private QueryRewriteResult rewriteResult;
    private String rewrittenQuery;
    private List<String> subQueries = List.of();
    private List<ChatIntentType> intents = List.of();
    private boolean ambiguous;
    private String guidanceQuestion;
    private ChatResponse chatResponse;

    // 创建会话执行上下文。
    private ChatExecutionContext(AuthUser authUser, ChatRequest request, boolean streamMode) {
        this.authUser = authUser;
        this.request = request;
        this.streamMode = streamMode;
    }

    // 创建普通非流式会话上下文。
    public static ChatExecutionContext sync(AuthUser authUser, ChatRequest request) {
        return new ChatExecutionContext(authUser, request, false);
    }

    // 创建流式会话上下文。
    public static ChatExecutionContext stream(AuthUser authUser, ChatRequest request) {
        return new ChatExecutionContext(authUser, request, true);
    }

    public AuthUser authUser() {
        return authUser;
    }

    public ChatRequest request() {
        return request;
    }

    public boolean streamMode() {
        return streamMode;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public void setEmitter(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public ModelOption model() {
        return model;
    }

    public void setModel(ModelOption model) {
        this.model = model;
    }

    public ChatMessage latestUserMessage() {
        return latestUserMessage;
    }

    public void setLatestUserMessage(ChatMessage latestUserMessage) {
        this.latestUserMessage = latestUserMessage;
        this.originalQuery = latestUserMessage == null ? null : latestUserMessage.content();
    }

    public ChatConversation conversation() {
        return conversation;
    }

    public void setConversation(ChatConversation conversation) {
        this.conversation = conversation;
    }

    public List<CachedChatMessage> conversationMessages() {
        return conversationMessages;
    }

    public void setConversationMessages(List<CachedChatMessage> conversationMessages) {
        this.conversationMessages = conversationMessages == null
                ? new ArrayList<>()
                : new ArrayList<>(conversationMessages);
    }

    public List<CachedChatMessage> promptConversationMessages() {
        return promptConversationMessages;
    }

    public void setPromptConversationMessages(List<CachedChatMessage> promptConversationMessages) {
        this.promptConversationMessages = promptConversationMessages == null
                ? new ArrayList<>()
                : new ArrayList<>(promptConversationMessages);
    }

    public ConversationMemorySummary memorySummary() {
        return memorySummary;
    }

    public void setMemorySummary(ConversationMemorySummary memorySummary) {
        this.memorySummary = memorySummary;
    }

    public ChatMessageEntity userMessage() {
        return userMessage;
    }

    public void setUserMessage(ChatMessageEntity userMessage) {
        this.userMessage = userMessage;
    }

    public String originalQuery() {
        return originalQuery;
    }

    public String rewrittenQuery() {
        return rewrittenQuery;
    }

    public QueryRewriteResult rewriteResult() {
        return rewriteResult;
    }

    public void setRewriteResult(QueryRewriteResult rewriteResult) {
        this.rewriteResult = rewriteResult;
        if (rewriteResult != null) {
            this.rewrittenQuery = rewriteResult.rewrite();
            this.subQueries = rewriteResult.subQuestions();
        }
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    public List<String> subQueries() {
        return subQueries;
    }

    public void setSubQueries(List<String> subQueries) {
        this.subQueries = subQueries == null ? List.of() : List.copyOf(subQueries);
    }

    public List<ChatIntentType> intents() {
        return intents;
    }

    public void setIntents(List<ChatIntentType> intents) {
        this.intents = intents == null ? List.of() : List.copyOf(intents);
    }

    public boolean hasIntent(ChatIntentType intent) {
        return intents.contains(intent);
    }

    public boolean ambiguous() {
        return ambiguous;
    }

    public void setAmbiguous(boolean ambiguous) {
        this.ambiguous = ambiguous;
    }

    public String guidanceQuestion() {
        return guidanceQuestion;
    }

    public void setGuidanceQuestion(String guidanceQuestion) {
        this.guidanceQuestion = guidanceQuestion;
    }

    public ChatResponse chatResponse() {
        return chatResponse;
    }

    public void setChatResponse(ChatResponse chatResponse) {
        this.chatResponse = chatResponse;
    }
}
