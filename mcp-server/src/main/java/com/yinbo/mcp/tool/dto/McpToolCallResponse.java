package com.yinbo.mcp.tool.dto;

import java.util.Map;

// MCP 工具执行后的统一响应。
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

    public static McpToolCallResponse success(String toolId, String message, Map<String, Object> data, long durationMs) {
        return new McpToolCallResponse(toolId, true, false, message, data, null, durationMs);
    }

    public static McpToolCallResponse clarification(String toolId, String message, long durationMs) {
        return new McpToolCallResponse(toolId, true, true, message, Map.of(), null, durationMs);
    }

    public static McpToolCallResponse failure(String toolId, String message, String errorMessage, long durationMs) {
        return new McpToolCallResponse(toolId, false, false, message, Map.of(), errorMessage, durationMs);
    }
}
