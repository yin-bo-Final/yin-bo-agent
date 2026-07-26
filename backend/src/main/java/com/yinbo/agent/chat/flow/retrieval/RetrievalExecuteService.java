package com.yinbo.agent.chat.flow.retrieval;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.intent.model.IntentKind;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.NodeScore;
import com.yinbo.agent.chat.flow.intent.model.SubQuestionIntent;
import com.yinbo.agent.infra.mcp.McpToolClient;
import com.yinbo.agent.infra.mcp.dto.McpToolCallRequest;
import com.yinbo.agent.infra.mcp.dto.McpToolCallResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
// 会话多通道检索执行服务。
public class RetrievalExecuteService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalExecuteService.class);

    private final McpToolClient mcpToolClient;

    public RetrievalExecuteService(McpToolClient mcpToolClient) {
        this.mcpToolClient = mcpToolClient;
    }

    // 执行知识库和工具检索，当前返回空结果占位。
    public RetrievalContext retrieve(ChatExecutionContext ctx) {
        List<String> knowledgeSnippets = List.of();
        List<String> toolResults = executeMcpTools(ctx);
        return new RetrievalContext(knowledgeSnippets, toolResults);
    }

    // 生成检索为空时的兜底回复。
    public String emptyRetrievalMessage(RetrievalContext retrievalContext) {
        if (retrievalContext == null || !retrievalContext.isEmpty()) {
            return null;
        }
        return "未检索到与问题相关的文档。";
    }

    private List<String> executeMcpTools(ChatExecutionContext ctx) {
        if (ctx.intentResult() == null || ctx.intentResult().selectedNodeScores().isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        Set<String> executedToolIds = new LinkedHashSet<>();
        for (NodeScore score : ctx.intentResult().selectedNodeScores()) {
            IntentNode node = score.node();
            if (node == null || node.getKind() != IntentKind.MCP || node.getMcpToolId() == null || node.getMcpToolId().isBlank()) {
                continue;
            }
            String toolId = node.getMcpToolId().trim();
            if (!executedToolIds.add(toolId)) {
                continue;
            }
            results.add(callMcpTool(ctx, node, toolId));
        }
        return List.copyOf(results);
    }

    private String callMcpTool(ChatExecutionContext ctx, IntentNode node, String toolId) {
        String query = queryForNode(ctx, node);
        try {
            McpToolCallResponse response = mcpToolClient.call(toolId, new McpToolCallRequest(
                    query,
                    ctx.conversation() == null ? null : ctx.conversation().getConversationNo(),
                    ctx.authUser() == null ? null : ctx.authUser().getId(),
                    Map.of()
            ));
            if (response.message() != null && !response.message().isBlank()) {
                return response.message();
            }
            if (!response.success()) {
                return "工具调用失败，请稍后重试。";
            }
            return "工具调用成功，但没有返回可展示内容。";
        } catch (Exception exception) {
            log.warn(
                    "event=mcp_tool_call_failed conversationId={} toolId={} type={} message={}",
                    ctx.conversation() == null ? "-" : ctx.conversation().getConversationNo(),
                    toolId,
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            return "物流查询工具暂时不可用，请稍后重试。";
        }
    }

    private String queryForNode(ChatExecutionContext ctx, IntentNode node) {
        if (ctx.intentResult() != null && node.getId() != null) {
            for (SubQuestionIntent subQuestionIntent : ctx.intentResult().subQuestionIntents()) {
                boolean matched = subQuestionIntent.nodeScores().stream()
                        .map(NodeScore::node)
                        .filter(item -> item != null && item.getId() != null)
                        .anyMatch(item -> item.getId().equals(node.getId()));
                if (matched && subQuestionIntent.subQuestion() != null && !subQuestionIntent.subQuestion().isBlank()) {
                    return subQuestionIntent.subQuestion();
                }
            }
        }
        if (ctx.rewrittenQuery() != null && !ctx.rewrittenQuery().isBlank()) {
            return ctx.rewrittenQuery();
        }
        return ctx.originalQuery() == null ? "" : ctx.originalQuery();
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
