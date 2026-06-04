package com.yinbo.ai.infra.embedding;

import com.yinbo.ai.api.embedding.EmbeddingService;
import com.yinbo.ai.infra.enums.ModelCapability;
import com.yinbo.ai.infra.model.ModelRoutingExecutor;
import com.yinbo.ai.infra.model.ModelSelector;
import com.yinbo.ai.infra.model.ModelTarget;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
// 带模型路由和故障转移的向量化服务。
public class RoutingEmbeddingService implements EmbeddingService {

    private final ModelSelector modelSelector;
    private final ModelRoutingExecutor routingExecutor;
    private final Map<String, EmbeddingClient> embeddingClients;

    // 注入模型选择器、执行器和所有 Embedding 客户端。
    public RoutingEmbeddingService(
            ModelSelector modelSelector,
            ModelRoutingExecutor routingExecutor,
            List<EmbeddingClient> embeddingClients
    ) {
        this.modelSelector = modelSelector;
        this.routingExecutor = routingExecutor;
        this.embeddingClients = embeddingClients.stream().collect(Collectors.toMap(EmbeddingClient::provider, Function.identity()));
    }

    @Override
    // 批量生成向量。
    public List<float[]> embedBatch(List<String> texts, String modelId) {
        List<ModelTarget> targets = modelSelector.selectEmbeddingCandidates(modelId);
        return routingExecutor.executeWithFallback(
                ModelCapability.EMBEDDING,
                targets,
                target -> embeddingClients.get(target.providerId()),
                (client, target) -> client.embedBatch(texts, target)
        );
    }

    @Override
    // 返回所选模型的维度。
    public int dimension(String modelId) {
        return modelSelector.selectEmbeddingCandidates(modelId).stream()
                .map(ModelTarget::dimension)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(1024);
    }
}
