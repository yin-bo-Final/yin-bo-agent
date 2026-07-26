package com.yinbo.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mcp")
// MCP 远程工具服务配置。
public record McpProperties(
        String baseUrl,
        Duration requestTimeout
) {

    public McpProperties {
        baseUrl = trimTrailingSlash(baseUrl == null || baseUrl.isBlank() ? "http://localhost:8083" : baseUrl);
        requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? Duration.ofSeconds(10)
                : requestTimeout;
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
