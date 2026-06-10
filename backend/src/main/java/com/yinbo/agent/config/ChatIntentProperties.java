package com.yinbo.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat.intent")
// 会话意图识别默认配置。
public record ChatIntentProperties(
        Boolean enabled,
        Boolean llmEnabled,
        Double minScore,
        Double ambiguityMinScore,
        Double ambiguityScoreGap,
        Integer maxIntents,
        Integer classifyTimeoutMs,
        Duration cacheTtl
) {

    // 补齐意图识别默认配置。
    public ChatIntentProperties {
        enabled = enabled == null || enabled;
        llmEnabled = llmEnabled == null || llmEnabled;
        minScore = rangeOrDefault(minScore, 0.35D);
        ambiguityMinScore = rangeOrDefault(ambiguityMinScore, 0.55D);
        ambiguityScoreGap = rangeOrDefault(ambiguityScoreGap, 0.08D);
        maxIntents = maxIntents == null || maxIntents <= 0 ? 3 : Math.min(maxIntents, 8);
        classifyTimeoutMs = classifyTimeoutMs == null || classifyTimeoutMs <= 0
                ? 3_000
                : Math.min(classifyTimeoutMs, 30_000);
        cacheTtl = cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                ? Duration.ofMinutes(60)
                : cacheTtl;
    }

    // 评分参数必须落在 0 到 1。
    private static Double rangeOrDefault(Double value, double defaultValue) {
        if (value == null || value < 0D || value > 1D) {
            return defaultValue;
        }
        return value;
    }
}
