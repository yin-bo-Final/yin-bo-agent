package com.yinbo.agent.infra.mcp.dto;

import java.util.Map;

// 远程 MCP 工具调用响应。
public record McpToolCallResponse(
        String toolId,
        boolean success,
        boolean needClarification,
        String message,
        Map<String, Object> data,
        String errorMessage,
        Long durationMs
) {

    public McpToolCallResponse {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
