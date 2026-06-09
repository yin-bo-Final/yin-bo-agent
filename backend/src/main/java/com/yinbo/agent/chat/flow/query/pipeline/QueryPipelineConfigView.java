package com.yinbo.agent.chat.flow.query.pipeline;

import java.time.LocalDateTime;

// 查询预处理流水线配置视图。
public record QueryPipelineConfigView(
        boolean terminologyEnabled,
        boolean llmRewriteEnabled,
        boolean ruleSplitEnabled,
        String fallbackPolicy,
        int rewriteTimeoutMs,
        int rewriteContextTurns,
        LocalDateTime updatedAt
) {
}
