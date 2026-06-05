package com.yinbo.agent.chat.flow.retrieval;

import java.util.List;

// 多通道检索阶段产生的上下文结果。
public record RetrievalContext(
        List<String> knowledgeSnippets,
        List<String> toolResults
) {

    // 规范化检索结果列表。
    public RetrievalContext {
        knowledgeSnippets = knowledgeSnippets == null ? List.of() : List.copyOf(knowledgeSnippets);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }

    // 创建空检索结果。
    public static RetrievalContext empty() {
        return new RetrievalContext(List.of(), List.of());
    }

    // 判断检索阶段是否没有任何可用结果。
    public boolean isEmpty() {
        return knowledgeSnippets.isEmpty() && toolResults.isEmpty();
    }
}
