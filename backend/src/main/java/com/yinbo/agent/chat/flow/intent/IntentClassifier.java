package com.yinbo.agent.chat.flow.intent;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.NodeScore;
import com.yinbo.agent.config.ChatIntentProperties;
import com.yinbo.ai.api.chat.LLMMessage;
import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
// 基于 LLM 的叶子意图节点打分器。
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private final LLMService llmService;
    private final IntentClassificationParser parser;
    private final ChatIntentProperties properties;

    // 注入 LLM 服务、解析器和意图配置。
    public IntentClassifier(
            LLMService llmService,
            IntentClassificationParser parser,
            ChatIntentProperties properties
    ) {
        this.llmService = llmService;
        this.parser = parser;
        this.properties = properties;
    }

    // 对候选叶子节点打分。
    public List<NodeScore> classify(ChatExecutionContext ctx, String question, List<IntentNode> candidateLeaves) {
        if (!properties.llmEnabled() || candidateLeaves == null || candidateLeaves.isEmpty()) {
            return List.of();
        }
        try {
            String response = callModel(ctx, question, candidateLeaves);
            Map<String, IntentNode> nodeById = candidateLeaves.stream()
                    .filter(node -> node.getId() != null)
                    .collect(Collectors.toMap(IntentNode::getId, node -> node, (left, right) -> left));
            return parser.parse(response, nodeById);
        } catch (Exception exception) {
            log.warn(
                    "event=intent_classification_failed conversationId={} modelId={} type={} message={}",
                    ctx.conversation() == null ? "-" : ctx.conversation().getConversationNo(),
                    ctx.model() == null ? "-" : ctx.model().id(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
            return List.of();
        }
    }

    private String callModel(ChatExecutionContext ctx, String question, List<IntentNode> candidateLeaves) throws Exception {
        LLMRequest request = new LLMRequest(
                ctx.model().id(),
                false,
                List.of(
                        new LLMMessage("system", systemPrompt()),
                        new LLMMessage("user", userPrompt(ctx, question, candidateLeaves))
                )
        );
        LLMResponse response = llmService.chat(request);
        String content = response == null ? null : response.content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("意图分类模型返回空内容");
        }
        return content.trim();
    }

    private String systemPrompt() {
        return """
                你是电商智能客服系统的意图分类器，只负责给候选意图节点打分，不回答用户问题。

                评分规则：
                - 0.85 到 1.00：用户问题明确匹配该节点，可直接路由。
                - 0.55 到 0.84：用户问题可能匹配该节点，存在语义重叠或需要结合上下文。
                - 0.35 到 0.54：弱相关，只在没有更强候选时保留。
                - 低于 0.35：不要返回。
                - 如果问题只是问候、感谢或询问助手身份，只匹配 SYSTEM 节点。
                - 如果问题询问实时订单、快递轨迹、个人订单状态，优先匹配 MCP 节点。
                - 如果问题询问政策、规则、流程、知识说明，优先匹配 KB 节点。

                输出要求：
                严格返回 JSON 数组，不要 Markdown，不要额外文字。
                最多返回 3 个对象，按 score 从高到低排列。
                每个对象格式：{"id":"节点 ID","score":0.92,"reason":"简短原因"}。
                没有可靠匹配时返回 []。
                """;
    }

    private String userPrompt(ChatExecutionContext ctx, String question, List<IntentNode> candidateLeaves) {
        return """
                # 用户上下文
                用户昵称：%s
                用户角色：%s

                # 用户问题
                %s

                # 候选叶子意图
                %s
                """.formatted(
                ctx.authUser() == null ? "未知" : nullToDash(ctx.authUser().getDisplayName()),
                ctx.authUser() == null ? "未知" : nullToDash(ctx.authUser().getRole()),
                nullToDash(question),
                serializeLeaves(candidateLeaves)
        );
    }

    private String serializeLeaves(List<IntentNode> leaves) {
        StringBuilder builder = new StringBuilder();
        for (IntentNode node : leaves) {
            builder.append("- id=").append(node.getId()).append('\n');
            builder.append("  path=").append(node.getFullPath()).append('\n');
            builder.append("  kind=").append(node.getKind()).append('\n');
            builder.append("  description=").append(nullToDash(node.getDescription())).append('\n');
            if (node.getMcpToolId() != null && !node.getMcpToolId().isBlank()) {
                builder.append("  toolId=").append(node.getMcpToolId()).append('\n');
            }
            if (node.getCollectionName() != null && !node.getCollectionName().isBlank()) {
                builder.append("  collection=").append(node.getCollectionName()).append('\n');
            }
            if (node.getExamples() != null && !node.getExamples().isEmpty()) {
                builder.append("  examples=").append(String.join(" / ", node.getExamples())).append('\n');
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
