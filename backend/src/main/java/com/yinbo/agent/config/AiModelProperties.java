package com.yinbo.agent.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
// AI 模型列表配置。
public record AiModelProperties(List<ModelOption> models) {

    // 规范化模型列表配置。
    public AiModelProperties {
        models = models == null ? List.of() : List.copyOf(models);
    }

    // 按模型 ID 查找模型配置。
    public ModelOption findById(String modelId) {
        return models.stream()
                .filter(model -> model.id().equals(modelId))
                .findFirst()
                .orElseGet(() -> models.isEmpty()
                        ? new ModelOption(modelId, modelId, "custom", false)
                        : models.get(0));
    }

    // 单个 AI 模型配置项。
    public record ModelOption(
            String id,
            String name,
            String provider,
            boolean enabled
    ) {
    }
}
