package com.yinbo.agent.chat.flow.message;

// assistant 响应生成后的统一结果。
public record AssistantResponseResult(
        String modelId,
        String content,
        Long responseDurationMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String sourceType,
        String fallbackReason,
        boolean success
) {

    public AssistantResponseResult {
        sourceType = sourceType == null || sourceType.isBlank() ? "LLM" : sourceType.trim();
        fallbackReason = fallbackReason == null || fallbackReason.isBlank() ? null : fallbackReason.trim();
        if (success) {
            fallbackReason = null;
        }
    }

    // 仅替换响应内容，保留模型和统计信息。
    public AssistantResponseResult withContent(String nextContent) {
        return new AssistantResponseResult(
                modelId,
                nextContent,
                responseDurationMs,
                promptTokens,
                completionTokens,
                totalTokens,
                sourceType,
                fallbackReason,
                success
        );
    }

    public static AssistantResponseResult llm(
            String modelId,
            String content,
            Long responseDurationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        return new AssistantResponseResult(
                modelId,
                content,
                responseDurationMs,
                promptTokens,
                completionTokens,
                totalTokens,
                "LLM",
                null,
                true
        );
    }

    public static AssistantResponseResult fallback(
            String modelId,
            String content,
            Long responseDurationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String fallbackReason
    ) {
        return new AssistantResponseResult(
                modelId,
                content,
                responseDurationMs,
                promptTokens,
                completionTokens,
                totalTokens,
                "FALLBACK",
                fallbackReason,
                false
        );
    }

    public static AssistantResponseResult staticContent(
            String modelId,
            String content,
            Long responseDurationMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        return new AssistantResponseResult(
                modelId,
                content,
                responseDurationMs,
                promptTokens,
                completionTokens,
                totalTokens,
                "STATIC",
                null,
                true
        );
    }
}
