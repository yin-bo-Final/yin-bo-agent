package com.yinbo.agent.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// 管理后台更新查询预处理流水线配置请求。
public record UpdateQueryPipelineConfigRequest(
        Boolean terminologyEnabled,
        Boolean llmRewriteEnabled,
        Boolean ruleSplitEnabled,

        @Size(max = 32, message = "降级策略不能超过 32 个字符")
        String fallbackPolicy,

        @Min(value = 500, message = "改写超时时间不能低于 500ms")
        @Max(value = 30000, message = "改写超时时间不能超过 30000ms")
        Integer rewriteTimeoutMs,

        @Min(value = 1, message = "上下文轮数不能低于 1")
        @Max(value = 10, message = "上下文轮数不能超过 10")
        Integer rewriteContextTurns
) {
}
