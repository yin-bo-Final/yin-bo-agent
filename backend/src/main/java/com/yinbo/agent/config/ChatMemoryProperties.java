package com.yinbo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat.memory")
// 会话记忆压缩和 Prompt 预算配置。
public record ChatMemoryProperties(
        Integer contextMaxTokens,
        Integer outputReserveTokens,
        Integer ragReserveTokens,
        Integer toolReserveTokens,
        Integer safetyMarginTokens,
        Integer recentWindowTokens,
        Integer headMessageCount,
        Integer minCompressMessageCount,
        Integer compressionWindowTokens,
        Integer maxSummaryTokens,
        Double autoCompressThresholdRatio,
        String compressionVersion
) {

    // 补齐上下文压缩配置默认值。
    public ChatMemoryProperties {
        contextMaxTokens = positiveOrDefault(contextMaxTokens, 100_000);
        outputReserveTokens = nonNegativeOrDefault(outputReserveTokens, 8_000);
        ragReserveTokens = nonNegativeOrDefault(ragReserveTokens, 12_000);
        toolReserveTokens = nonNegativeOrDefault(toolReserveTokens, 4_000);
        safetyMarginTokens = nonNegativeOrDefault(safetyMarginTokens, 4_000);
        recentWindowTokens = positiveOrDefault(recentWindowTokens, 20_000);
        headMessageCount = nonNegativeOrDefault(headMessageCount, 4);
        minCompressMessageCount = positiveOrDefault(minCompressMessageCount, 8);
        compressionWindowTokens = positiveOrDefault(compressionWindowTokens, 24_000);
        maxSummaryTokens = positiveOrDefault(maxSummaryTokens, 4_000);
        autoCompressThresholdRatio = ratioOrDefault(autoCompressThresholdRatio, 0.9D);
        compressionVersion = compressionVersion == null || compressionVersion.isBlank()
                ? "v1"
                : compressionVersion.trim();
    }

    // 计算留给会话记忆的可用 token 预算。
    public int memoryBudgetTokens() {
        int budget = contextMaxTokens
                - outputReserveTokens
                - ragReserveTokens
                - toolReserveTokens
                - safetyMarginTokens;
        return Math.max(1_000, budget);
    }

    // 计算自动压缩硬触发 token 阈值。
    public int autoCompressThresholdTokens() {
        return Math.max(1, (int) Math.ceil(memoryBudgetTokens() * autoCompressThresholdRatio));
    }

    // 非正整数回退到默认值。
    private static Integer positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    // 负整数回退到默认值。
    private static Integer nonNegativeOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    // 非法比例回退到默认值。
    private static Double ratioOrDefault(Double value, double defaultValue) {
        return value == null || value <= 0D || value > 1D ? defaultValue : value;
    }
}
