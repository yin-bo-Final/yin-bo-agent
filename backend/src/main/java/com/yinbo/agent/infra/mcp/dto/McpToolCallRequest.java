package com.yinbo.agent.infra.mcp.dto;

import java.util.Map;

// 远程 MCP 工具调用请求。
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
