package com.yinbo.agent.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 单轮 assistant 回复的调试追踪信息，responseDurationMs 表示本轮聊天流水线端到端总耗时。
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatAssistantTraceResponse(
        Integer traceVersion,
        String modelId,
        Long responseDurationMs,
        Long llmDurationMs,
        Long otherDurationMs,
        Integer totalTokens,
        Boolean enteredRag,
        String fallbackReason,
        List<DurationStageTrace> durationStages,
        QueryRewriteTrace queryRewrite,
        IntentResolveTrace intentResolve,
        RagTrace rag
) {

    public static final int CURRENT_TRACE_VERSION = 1;

    public ChatAssistantTraceResponse {
        if (traceVersion == null || traceVersion < 1) {
            traceVersion = CURRENT_TRACE_VERSION;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DurationStageTrace(
            String code,
            String label,
            Long durationMs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueryRewriteTrace(
            String originalQuery,
            String normalizedQuery,
            String rewrittenQuery,
            Boolean shouldSplit,
            List<String> subQuestions,
            List<TermTrace> matchedTerms,
            String sourceType,
            Boolean success,
            String fallbackReason,
            Long durationMs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TermTrace(
            String raw,
            String canonical,
            String termType
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntentResolveTrace(
            List<String> intents,
            String outcome,
            Boolean ambiguous,
            String guidanceQuestion,
            List<NodeTrace> selectedNodes,
            List<SubQuestionIntentTrace> subQuestionIntents,
            Boolean success,
            String fallbackReason,
            Long durationMs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubQuestionIntentTrace(
            String question,
            List<NodeTrace> nodes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeTrace(
            String nodeCode,
            String path,
            String kind,
            Double score,
            String source,
            String reason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RagTrace(
            Boolean entered,
            Integer knowledgeSnippetCount,
            Integer toolResultCount,
            Long durationMs
    ) {
    }
}
