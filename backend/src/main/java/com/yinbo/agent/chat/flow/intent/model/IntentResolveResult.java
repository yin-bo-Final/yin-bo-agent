package com.yinbo.agent.chat.flow.intent.model;

import java.util.List;

// 一轮意图识别的完整结果。
public record IntentResolveResult(
        List<SubQuestionIntent> subQuestionIntents,
        List<NodeScore> selectedNodeScores,
        boolean ambiguous,
        String guidanceQuestion
) {

    public IntentResolveResult {
        subQuestionIntents = subQuestionIntents == null ? List.of() : List.copyOf(subQuestionIntents);
        selectedNodeScores = selectedNodeScores == null ? List.of() : List.copyOf(selectedNodeScores);
    }

    public static IntentResolveResult empty() {
        return new IntentResolveResult(List.of(), List.of(), false, null);
    }
}
