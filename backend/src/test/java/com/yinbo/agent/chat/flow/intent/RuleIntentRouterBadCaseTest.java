package com.yinbo.agent.chat.flow.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yinbo.agent.chat.flow.intent.RuleIntentRouter.RuleRouteResult;
import com.yinbo.agent.chat.flow.intent.model.IntentKind;
import com.yinbo.agent.chat.flow.intent.model.IntentLevel;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentRule;
import com.yinbo.agent.chat.flow.intent.model.IntentRuleMatchMode;
import com.yinbo.agent.chat.flow.intent.model.IntentRuleType;
import com.yinbo.agent.chat.flow.intent.model.IntentTreeData;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class RuleIntentRouterBadCaseTest {

    private final IntentRuleService intentRuleService = mock(IntentRuleService.class);
    private final RuleIntentRouter router = new RuleIntentRouter(intentRuleService);
    private final IntentTreeData treeData = buildTreeData();

    @ParameterizedTest
    @CsvFileSource(resources = "/intent-rule-bad-cases.csv", delimiter = '|', numLinesToSkip = 1)
    void routesStrongRulesWithoutFalsePositive(String question, String expectedStrongNodeCode) {
        when(intentRuleService.enabledRules()).thenReturn(defaultRules());

        RuleRouteResult result = router.route(question, treeData);

        if ("NONE".equals(expectedStrongNodeCode)) {
            assertThat(result.strongScores())
                    .as("question should not trigger strong rule: %s", question)
                    .isEmpty();
            return;
        }
        assertThat(result.strongScores())
                .as("question should trigger expected strong node: %s", question)
                .isNotEmpty();
        assertThat(result.strongScores().get(0).node().getId()).isEqualTo(expectedStrongNodeCode);
    }

    private List<IntentRule> defaultRules() {
        return List.of(
                strong(
                        "system-greeting-strong",
                        "问候强命中",
                        "system-greeting",
                        List.of("你好", "您好", "hi", "hello", "在吗", "谢谢", "感谢"),
                        List.of(),
                        List.of(),
                        0.98D
                ),
                strong(
                        "system-about-strong",
                        "助手介绍强命中",
                        "system-about",
                        List.of("你是谁", "你是干嘛", "你能做什么", "你的能力", "介绍一下你"),
                        List.of(),
                        List.of(),
                        0.96D
                ),
                strong(
                        "logistics-tracking-strong",
                        "物流轨迹强命中",
                        "logistics-tracking",
                        List.of("快递", "包裹", "物流"),
                        List.of("到哪", "哪里", "在哪", "进度", "轨迹", "状态", "查"),
                        List.of("运费", "清关", "配送规则", "快递公司", "什么意思", "是什么意思", "含义", "概念", "定义", "解释一下", "指什么", "这句话"),
                        0.95D
                ),
                strong(
                        "order-query-strong",
                        "订单查询强命中",
                        "order-query",
                        List.of("订单"),
                        List.of("查询", "查", "状态", "详情", "支付", "到哪", "进度"),
                        List.of("退货政策", "退款规则", "订单规则", "什么意思", "是什么意思", "含义", "概念", "定义", "解释一下", "指什么", "这句话"),
                        0.94D
                )
        );
    }

    private IntentRule strong(
            String ruleCode,
            String name,
            String targetNodeCode,
            List<String> includeKeywords,
            List<String> requireKeywords,
            List<String> excludeKeywords,
            double score
    ) {
        return new IntentRule(
                ruleCode,
                ruleCode,
                name,
                null,
                targetNodeCode,
                IntentRuleType.STRONG,
                includeKeywords,
                IntentRuleMatchMode.ANY,
                requireKeywords,
                IntentRuleMatchMode.ANY,
                excludeKeywords,
                score,
                true,
                null,
                null
        );
    }

    private IntentTreeData buildTreeData() {
        IntentNode logisticsTracking = leaf("logistics-tracking", "物流轨迹查询", IntentKind.MCP);
        IntentNode orderQuery = leaf("order-query", "订单查询", IntentKind.MCP);
        IntentNode systemGreeting = leaf("system-greeting", "欢迎与问候", IntentKind.SYSTEM);
        IntentNode systemAbout = leaf("system-about", "关于助手", IntentKind.SYSTEM);
        List<IntentNode> roots = List.of(logisticsTracking, orderQuery, systemGreeting, systemAbout);
        return new IntentTreeData(
                roots,
                roots,
                roots,
                roots.stream().collect(java.util.stream.Collectors.toMap(IntentNode::getId, node -> node))
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
