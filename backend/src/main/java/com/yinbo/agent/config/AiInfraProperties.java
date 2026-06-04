package com.yinbo.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-infra")
// AI 基础设施远程服务配置。
public record AiInfraProperties(
        String baseUrl,
        Duration requestTimeout
) {

    // 规范化 ai-infra HTTP 连接配置。
    public AiInfraProperties {
        baseUrl = trimTrailingSlash(baseUrl == null || baseUrl.isBlank() ? "http://localhost:8082" : baseUrl);
        requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? Duration.ofMinutes(5)
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
