package com.yinbo.ai.infra.model;

import com.yinbo.ai.infra.enums.ModelCapability;
import com.yinbo.ai.infra.config.AiModelProperties;

// 一次模型调用的目标配置。
public record ModelTarget(
        ModelCapability capability,
        String id,
        AiModelProperties.ModelCandidate candidate,
        AiModelProperties.ProviderConfig provider
) {

    // 返回供应商标识。
    public String providerId() {
        return candidate.provider();
    }

    // 返回传给供应商的真实模型名。
    public String modelName() {
        return candidate.model();
    }

    // 返回最终请求 URL。
    public String requestUrl() {
        if (candidate.url() != null && !candidate.url().isBlank()) {
            return candidate.url();
        }
        String baseUrl = provider.url();
        String endpoint = provider.endpoint(capability.name());
        if (endpoint == null || endpoint.isBlank()) {
            return baseUrl;
        }
        if (endpoint.startsWith("/")) {
            return baseUrl + endpoint;
        }
        return baseUrl + "/" + endpoint;
    }

    // 返回 Embedding 向量维度。
    public Integer dimension() {
        return candidate.dimension();
    }
}
