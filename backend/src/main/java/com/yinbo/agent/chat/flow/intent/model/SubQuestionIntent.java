package com.yinbo.agent.chat.flow.intent.model;

import java.util.List;

// 单个子问题和它命中的候选意图。
public record SubQuestionIntent(
        String subQuestion,
        List<NodeScore> nodeScores
) {

    public SubQuestionIntent {
        nodeScores = nodeScores == null ? List.of() : List.copyOf(nodeScores);
    }
}
