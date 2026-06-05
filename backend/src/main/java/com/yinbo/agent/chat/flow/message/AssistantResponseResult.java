package com.yinbo.agent.chat.flow.message;

// assistant 响应生成后的统一结果。
public record AssistantResponseResult(
        String modelId,
        String content,
        Long responseDurationMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {

    // 仅替换响应内容，保留模型和统计信息。
    public AssistantResponseResult withContent(String nextContent) {
        return new AssistantResponseResult(modelId, nextContent, responseDurationMs, promptTokens, completionTokens, totalTokens);
    }
}
