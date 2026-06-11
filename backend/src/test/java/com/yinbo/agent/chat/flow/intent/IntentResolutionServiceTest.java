package com.yinbo.agent.chat.flow.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.yinbo.agent.chat.flow.context.ChatIntentType;
import com.yinbo.agent.chat.flow.intent.RuleIntentRouter.RuleRouteResult;
import com.yinbo.agent.chat.flow.intent.model.IntentKind;
import com.yinbo.agent.chat.flow.intent.model.IntentLevel;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentTreeData;
import com.yinbo.agent.chat.flow.intent.model.NodeScore;
import com.yinbo.agent.config.ChatIntentProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IntentResolutionServiceTest {

    private final IntentTreeService intentTreeService = mock(IntentTreeService.class);
    private final RuleIntentRouter ruleIntentRouter = mock(RuleIntentRouter.class);
    private final IntentClassifier intentClassifier = mock(IntentClassifier.class);
    private final IntentResolveRecordService recordService = mock(IntentResolveRecordService.class);
    private final ChatIntentProperties properties = new ChatIntentProperties(
            true,
            true,
            0.35D,
            0.55D,
            0.08D,
            3,
            3_000,
            Duration.ofMinutes(60)
    );
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final IntentResolutionService service = new IntentResolutionService(
            intentTreeService,
            ruleIntentRouter,
            intentClassifier,
            recordService,
            properties,
            executor
    );

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void skipsIntentClassifierForDirectAnswerQuestion() {
        IntentNode logisticsNode = leaf("logistics-tracking", "物流轨迹查询", IntentKind.MCP);
        IntentTreeData treeData = treeData(logisticsNode);
        ChatExecutionContext ctx = context("快递什么意思");
        when(intentTreeService.loadEnabledTreeData()).thenReturn(treeData);
        when(ruleIntentRouter.route("快递什么意思", treeData)).thenReturn(new RuleRouteResult(
                List.of(),
                List.of(logisticsNode),
                true,
                "WEAK_RULE"
        ));

        service.resolve(ctx);

        assertThat(ctx.intents()).containsExactly(ChatIntentType.DIRECT_CHAT);
        assertThat(ctx.intentResult().selectedNodeScores()).isEmpty();
        assertThat(ctx.intentResult().fallbackReason()).isNull();
        verify(intentClassifier, never()).classify(any(), anyString(), any());
        verify(recordService).record(eq(ctx), eq(ctx.intentResult()), anyLong());
    }

    @Test
    void classifiesAgainstAllLeavesWhenRuleRouterHasNoCandidate() {
        IntentNode logisticsNode = leaf("logistics-tracking", "物流轨迹查询", IntentKind.MCP);
        IntentTreeData treeData = treeData(logisticsNode);
        ChatExecutionContext ctx = context("查一下物流进度");
        when(intentTreeService.loadEnabledTreeData()).thenReturn(treeData);
        when(ruleIntentRouter.route("查一下物流进度", treeData)).thenReturn(RuleRouteResult.empty());
        when(intentClassifier.classify(eq(ctx), eq("查一下物流进度"), eq(List.of(logisticsNode))))
                .thenReturn(List.of(new NodeScore(logisticsNode, 0.9D, "模型命中", "LLM")));

        service.resolve(ctx);

        assertThat(ctx.intents()).containsExactly(ChatIntentType.TOOL_CALL);
        assertThat(ctx.intentResult().selectedNodeScores()).hasSize(1);
        verify(intentClassifier).classify(eq(ctx), eq("查一下物流进度"), eq(List.of(logisticsNode)));
    }

    private ChatExecutionContext context(String question) {
        AuthUser authUser = new AuthUser();
        authUser.setId(1L);
        ChatRequest request = new ChatRequest(
                null,
                "qwen",
                List.of(new ChatMessage("user", question)),
                false
        );
        ChatExecutionContext ctx = ChatExecutionContext.sync(authUser, request);
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationNo("conv-1");
        ctx.setConversation(conversation);
        ctx.setLatestUserMessage(new ChatMessage("user", question));
        ctx.setSubQueries(List.of(question));
        return ctx;
    }

    private IntentTreeData treeData(IntentNode... nodes) {
        List<IntentNode> nodeList = List.of(nodes);
        return new IntentTreeData(
                nodeList,
                nodeList,
                nodeList,
                nodeList.stream().collect(java.util.stream.Collectors.toMap(IntentNode::getId, node -> node))
        );
    }

    private IntentNode leaf(String id, String name, IntentKind kind) {
        IntentNode node = new IntentNode();
        node.setId(id);
        node.setName(name);
        node.setFullPath(name);
        node.setLevel(IntentLevel.CATEGORY);
        node.setKind(kind);
        return node;
    }
}
