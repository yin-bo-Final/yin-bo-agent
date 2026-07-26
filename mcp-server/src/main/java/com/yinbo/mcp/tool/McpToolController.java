package com.yinbo.mcp.tool;

import com.yinbo.mcp.tool.dto.McpToolCallRequest;
import com.yinbo.mcp.tool.dto.McpToolCallResponse;
import com.yinbo.mcp.tool.dto.McpToolDescriptor;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
// MCP 工具内部调用接口。
public class McpToolController {

    private final LogisticsTrackingToolService logisticsTrackingToolService;

    public McpToolController(LogisticsTrackingToolService logisticsTrackingToolService) {
        this.logisticsTrackingToolService = logisticsTrackingToolService;
    }

    @GetMapping("/internal/mcp/tools")
    // 查询 MCP 服务当前支持的工具。
    public List<McpToolDescriptor> tools() {
        return List.of(logisticsTrackingToolService.descriptor());
    }

    @PostMapping("/internal/mcp/tools/{toolId}/call")
    // 调用指定 MCP 工具。
    public McpToolCallResponse call(
            @PathVariable String toolId,
            @RequestBody McpToolCallRequest request
    ) {
        if (LogisticsTrackingToolService.TOOL_ID.equals(toolId)) {
            return logisticsTrackingToolService.call(request);
        }
        throw new UnsupportedToolException(toolId);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class UnsupportedToolException extends RuntimeException {

        private UnsupportedToolException(String toolId) {
            super("unsupported MCP tool: " + toolId);
        }
    }
}
