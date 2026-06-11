package com.yinbo.agent.chat.flow.intent.model;

import java.util.List;

// 一轮意图识别的完整结果。
public record IntentResolveResult(
        List<SubQuestionIntent> subQuestionIntents,
        List<NodeScore> selectedNodeScores,
        boolean ambiguous,
        String guidanceQuestion,
        IntentResolveOutcome outcome,
        String fallbackReason
) {

    public IntentResolveResult {
        subQuestionIntents = subQuestionIntents == null ? List.of() : List.copyOf(subQuestionIntents);
        selectedNodeScores = selectedNodeScores == null ? List.of() : List.copyOf(selectedNodeScores);
        fallbackReason = normalizeFallbackReason(fallbackReason);
        outcome = outcome == null
                ? (fallbackReason == null ? IntentResolveOutcome.SUCCESS : IntentResolveOutcome.FALLBACK)
                : outcome;
        if (outcome == IntentResolveOutcome.FALLBACK && fallbackReason == null) {
            fallbackReason = "UNKNOWN";
        }
        if (outcome != IntentResolveOutcome.FALLBACK) {
            fallbackReason = null;
        }
    }

    public static IntentResolveResult empty() {
        return new IntentResolveResult(List.of(), List.of(), false, null, IntentResolveOutcome.SUCCESS, null);
    }

    public static IntentResolveResult fallback(String fallbackReason) {
        return new IntentResolveResult(
                List.of(),
                List.of(),
                false,
                null,
                IntentResolveOutcome.FALLBACK,
                fallbackReason
        );
    }

    public static IntentResolveResult success(
            List<SubQuestionIntent> subQuestionIntents,
            List<NodeScore> selectedNodeScores,
            boolean ambiguous,
            String guidanceQuestion
    ) {
        return new IntentResolveResult(
                subQuestionIntents,
                selectedNodeScores,
                ambiguous,
                guidanceQuestion,
                IntentResolveOutcome.SUCCESS,
                null
        );
    }

    public IntentResolveResult asFallback(String fallbackReason) {
        return new IntentResolveResult(
                subQuestionIntents,
                selectedNodeScores,
                ambiguous,
                guidanceQuestion,
                IntentResolveOutcome.FALLBACK,
                fallbackReason
        );
    }

    public boolean success() {
        return outcome == IntentResolveOutcome.SUCCESS;
    }

    private static String normalizeFallbackReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
