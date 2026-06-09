package com.yinbo.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat.query-rewrite")
// 查询改写、问题拆分和术语统一的默认配置。
public record ChatQueryRewriteProperties(
        Boolean terminologyEnabled,
        Boolean llmRewriteEnabled,
        Boolean ruleSplitEnabled,
        String fallbackPolicy,
        Integer rewriteTimeoutMs,
        Integer rewriteContextTurns,
        Duration terminologyCacheTtl,
        Duration pipelineConfigCacheTtl
) {

    // 补齐查询改写默认配置。
    public ChatQueryRewriteProperties {
        terminologyEnabled = terminologyEnabled == null || terminologyEnabled;
        llmRewriteEnabled = llmRewriteEnabled == null || llmRewriteEnabled;
        ruleSplitEnabled = ruleSplitEnabled == null || ruleSplitEnabled;
        fallbackPolicy = fallbackPolicy == null || fallbackPolicy.isBlank()
                ? "TERM_ONLY"
                : fallbackPolicy.trim().toUpperCase();
        rewriteTimeoutMs = positiveOrDefault(rewriteTimeoutMs, 3_000);
        rewriteContextTurns = positiveOrDefault(rewriteContextTurns, 3);
        terminologyCacheTtl = terminologyCacheTtl == null || terminologyCacheTtl.isNegative() || terminologyCacheTtl.isZero()
                ? Duration.ofMinutes(60)
                : terminologyCacheTtl;
        pipelineConfigCacheTtl = pipelineConfigCacheTtl == null || pipelineConfigCacheTtl.isNegative() || pipelineConfigCacheTtl.isZero()
                ? Duration.ofMinutes(10)
                : pipelineConfigCacheTtl;
    }

    // 非正整数回退到默认值。
    private static Integer positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
