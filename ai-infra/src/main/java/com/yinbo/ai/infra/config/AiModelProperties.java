package com.yinbo.ai.infra.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
// AI 模型路由配置。
public record AiModelProperties(
        List<ModelOption> models,
        Map<String, ProviderConfig> providers,
        ModelGroup chat,
        ModelGroup embedding,
        ModelGroup rerank,
        Selection selection,
        Stream stream
) {

    // 规范化模型路由配置。
    public AiModelProperties {
        models = models == null ? List.of() : List.copyOf(models);
        providers = providers == null ? Map.of() : Map.copyOf(providers);
        chat = normalizeGroup(chat, models);
        embedding = normalizeGroup(embedding, List.of());
        rerank = normalizeGroup(rerank, List.of());
        selection = selection == null ? new Selection(null, null) : selection;
        stream = stream == null ? new Stream(null) : stream;
    }

    // 按模型 ID 查找前端展示用模型配置。
    public ModelOption findById(String modelId) {
        String resolvedModelId = blankToNull(modelId);
        return models().stream()
                .filter(model -> Objects.equals(model.id(), resolvedModelId))
                .findFirst()
                .orElseGet(() -> models().isEmpty()
                        ? new ModelOption(resolvedModelId, resolvedModelId, "custom", false)
                        : models().get(0));
    }

    // 根据供应商 ID 查找供应商配置。
    public ProviderConfig requireProvider(String providerId) {
        ProviderConfig provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("AI provider not configured: " + providerId);
        }
        return provider;
    }

    // 根据能力返回模型组。
    public ModelGroup group(String capability) {
        return switch (capability) {
            case "CHAT" -> chat;
            case "EMBEDDING" -> embedding;
            case "RERANK" -> rerank;
            default -> throw new IllegalArgumentException("Unsupported AI capability: " + capability);
        };
    }

    // 单个 AI 模型展示项。
    public record ModelOption(
            String id,
            String name,
            String provider,
            boolean enabled
    ) {
    }

    // 模型供应商配置。
    public record ProviderConfig(
            String url,
            String apiKey,
            Map<String, String> endpoints
    ) {

        // 规范化供应商连接配置。
        public ProviderConfig {
            url = trimTrailingSlash(url);
            apiKey = apiKey == null ? "" : apiKey.trim();
            endpoints = endpoints == null ? Map.of() : Map.copyOf(endpoints);
        }

        // 获取指定能力的端点路径。
        public String endpoint(String capability) {
            String endpoint = endpoints.get(capability.toLowerCase());
            return endpoint == null ? "" : endpoint.trim();
        }
    }

    // 某种能力下的一组候选模型。
    public record ModelGroup(
            String defaultModel,
            String deepThinkingModel,
            List<ModelCandidate> candidates
    ) {

        // 规范化候选模型列表。
        public ModelGroup {
            candidates = candidates == null ? List.of() : candidates.stream()
                    .map(ModelCandidate::normalized)
                    .sorted(Comparator.comparingInt(ModelCandidate::resolvedPriority))
                    .toList();
            defaultModel = blankToNull(defaultModel);
            deepThinkingModel = blankToNull(deepThinkingModel);
        }
    }

    // 单个可调用候选模型。
    public record ModelCandidate(
            String id,
            String name,
            String provider,
            String model,
            String url,
            Integer dimension,
            Integer priority,
            Boolean enabled,
            Boolean supportsThinking
    ) {

        // 规范化候选模型配置。
        public ModelCandidate normalized() {
            return new ModelCandidate(
                    blankToDefault(id, model),
                    blankToDefault(name, blankToDefault(id, model)),
                    blankToNull(provider),
                    blankToDefault(model, id),
                    trimTrailingSlash(url),
                    dimension,
                    priority == null ? 100 : priority,
                    enabled == null || enabled,
                    Boolean.TRUE.equals(supportsThinking)
            );
        }

        // 判断候选是否启用。
        public boolean enabledValue() {
            return enabled == null || enabled;
        }

        // 返回排序优先级。
        public int resolvedPriority() {
            return priority == null ? 100 : priority;
        }

        // 判断是否支持深度思考。
        public boolean supportsThinkingValue() {
            return Boolean.TRUE.equals(supportsThinking);
        }
    }

    // 模型熔断策略。
    public record Selection(
            Integer failureThreshold,
            Long openDurationMs
    ) {

        // 连续失败多少次后熔断。
        public int resolvedFailureThreshold() {
            return failureThreshold == null || failureThreshold <= 0 ? 2 : failureThreshold;
        }

        // 熔断窗口时间。
        public long resolvedOpenDurationMs() {
            return openDurationMs == null || openDurationMs <= 0 ? 30_000L : openDurationMs;
        }
    }

    // 流式输出配置。
    public record Stream(Integer messageChunkSize) {

        // 前端流式展示合并块大小。
        public int resolvedMessageChunkSize() {
            return messageChunkSize == null || messageChunkSize <= 0 ? 1 : messageChunkSize;
        }
    }

    // 兼容旧 models 配置，自动生成 Chat 候选。
    private static ModelGroup normalizeGroup(ModelGroup group, List<ModelOption> fallbackModels) {
        if (group != null && group.candidates() != null && !group.candidates().isEmpty()) {
            return group;
        }
        List<ModelCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < fallbackModels.size(); index++) {
            ModelOption option = fallbackModels.get(index);
            candidates.add(new ModelCandidate(
                    option.id(),
                    option.name(),
                    option.provider(),
                    option.id(),
                    null,
                    null,
                    index,
                    option.enabled(),
                    false
            ));
        }
        String defaultModel = group == null ? null : group.defaultModel();
        String deepThinkingModel = group == null ? null : group.deepThinkingModel();
        if (defaultModel == null && !candidates.isEmpty()) {
            defaultModel = candidates.get(0).id();
        }
        return new ModelGroup(defaultModel, deepThinkingModel, candidates);
    }

    private static String blankToDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? blankToNull(defaultValue) : normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
