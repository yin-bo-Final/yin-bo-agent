package com.yinbo.ai.infra.model;

import com.yinbo.ai.infra.enums.ModelCapability;
import com.yinbo.ai.infra.config.AiModelProperties;
import com.yinbo.ai.infra.http.ModelClientException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
// 模型候选选择器。
public class ModelSelector {

    private final AiModelProperties aiModelProperties;

    // 注入模型路由配置。
    public ModelSelector(AiModelProperties aiModelProperties) {
        this.aiModelProperties = aiModelProperties;
    }

    // 选择 Chat 候选模型。
    public List<ModelTarget> selectChatCandidates(String requestedModelId, boolean deepThinking) {
        AiModelProperties.ModelGroup group = aiModelProperties.chat();
        String preferredModelId = deepThinking && group.deepThinkingModel() != null
                ? group.deepThinkingModel()
                : firstNonBlank(requestedModelId, group.defaultModel());
        return select(ModelCapability.CHAT, preferredModelId, deepThinking);
    }

    // 选择 Embedding 候选模型。
    public List<ModelTarget> selectEmbeddingCandidates(String requestedModelId) {
        return select(ModelCapability.EMBEDDING, firstNonBlank(requestedModelId, aiModelProperties.embedding().defaultModel()), false);
    }

    // 选择 Rerank 候选模型。
    public List<ModelTarget> selectRerankCandidates(String requestedModelId) {
        return select(ModelCapability.RERANK, firstNonBlank(requestedModelId, aiModelProperties.rerank().defaultModel()), false);
    }

    private List<ModelTarget> select(ModelCapability capability, String preferredModelId, boolean requireThinking) {
        AiModelProperties.ModelGroup group = aiModelProperties.group(capability.name());
        List<AiModelProperties.ModelCandidate> enabledCandidates = group.candidates().stream()
                .filter(AiModelProperties.ModelCandidate::enabledValue)
                .filter(candidate -> !requireThinking || candidate.supportsThinkingValue())
                .sorted(Comparator.comparingInt(AiModelProperties.ModelCandidate::resolvedPriority))
                .toList();
        if (enabledCandidates.isEmpty()) {
            throw new ModelClientException("没有可用的 " + capability.name() + " 模型配置");
        }

        Map<String, AiModelProperties.ModelCandidate> ordered = new LinkedHashMap<>();
        if (preferredModelId != null && !preferredModelId.isBlank()) {
            enabledCandidates.stream()
                    .filter(candidate -> preferredModelId.equals(candidate.id()))
                    .findFirst()
                    .ifPresent(candidate -> ordered.put(candidate.id(), candidate));
        }
        enabledCandidates.forEach(candidate -> ordered.putIfAbsent(candidate.id(), candidate));

        List<ModelTarget> targets = new ArrayList<>();
        for (AiModelProperties.ModelCandidate candidate : ordered.values()) {
            AiModelProperties.ProviderConfig provider = aiModelProperties.requireProvider(candidate.provider());
            targets.add(new ModelTarget(capability, candidate.id(), candidate, provider));
        }
        return targets;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }
}
