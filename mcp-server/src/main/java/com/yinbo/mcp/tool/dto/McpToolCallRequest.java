package com.yinbo.mcp.tool.dto;

import java.util.Map;

// backend 远程调用 MCP 工具时传入的统一请求。
public record McpToolCallRequest(
        String query,
        String conversationId,
        Long userId,
        Map<String, Object> arguments
) {

    public McpToolCallRequest {
        query = query == null ? "" : query.trim();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
