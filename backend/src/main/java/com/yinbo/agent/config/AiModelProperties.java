package com.yinbo.agent.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiModelProperties(List<ModelOption> models) {

    public AiModelProperties {
        models = models == null ? List.of() : List.copyOf(models);
    }

    public ModelOption findById(String modelId) {
        return models.stream()
                .filter(model -> model.id().equals(modelId))
                .findFirst()
                .orElseGet(() -> models.isEmpty()
                        ? new ModelOption(modelId, modelId, "custom", false)
                        : models.get(0));
    }

    public record ModelOption(
            String id,
            String name,
            String provider,
            boolean enabled
    ) {
    }
}
