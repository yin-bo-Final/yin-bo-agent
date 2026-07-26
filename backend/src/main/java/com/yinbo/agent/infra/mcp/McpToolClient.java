package com.yinbo.agent.infra.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.config.McpProperties;
import com.yinbo.agent.infra.mcp.dto.McpToolCallRequest;
import com.yinbo.agent.infra.mcp.dto.McpToolCallResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
// backend 调用独立 MCP 工具服务的 HTTP 客户端。
public class McpToolClient {

    private final McpProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public McpToolClient(McpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // 远程调用指定 MCP 工具。
    public McpToolCallResponse call(String toolId, McpToolCallRequest request) {
        HttpRequest httpRequest = withRequestId(HttpRequest.newBuilder(uri("/internal/mcp/tools/" + toolId + "/call")))
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(request)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode(), response.body());
            return objectMapper.readValue(response.body(), McpToolCallResponse.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 工具调用被中断", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("MCP 工具调用失败：" + exception.getMessage(), exception);
        }
    }

    private HttpRequest.Builder withRequestId(HttpRequest.Builder builder) {
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            builder.header("X-Request-Id", requestId);
        }
        return builder;
    }

    private URI uri(String path) {
        return URI.create(properties.baseUrl() + path);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("MCP 工具请求序列化失败", exception);
        }
    }

    private void requireSuccess(int statusCode, String body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new IllegalStateException("MCP 工具调用失败 status=" + statusCode + " body=" + truncate(body));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }
}
