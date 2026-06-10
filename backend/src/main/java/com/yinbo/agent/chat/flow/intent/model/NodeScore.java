package com.yinbo.agent.chat.flow.intent.model;

// 意图节点匹配分数。
public record NodeScore(
        IntentNode node,
        double score,
        String reason,
        String source
) {
}
