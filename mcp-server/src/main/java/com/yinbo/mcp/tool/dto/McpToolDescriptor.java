package com.yinbo.mcp.tool.dto;

import java.util.List;

// MCP 工具描述，用于服务发现和文档展示。
public record McpToolDescriptor(
        String toolId,
        String name,
        String description,
        List<String> requiredArguments
) {

    public McpToolDescriptor {
        requiredArguments = requiredArguments == null ? List.of() : List.copyOf(requiredArguments);
    }
}
