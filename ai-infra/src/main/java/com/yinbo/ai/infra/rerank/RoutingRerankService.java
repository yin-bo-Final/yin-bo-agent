package com.yinbo.ai.infra.rerank;

import com.yinbo.ai.api.rerank.RerankService;
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
// 带模型路由和故障转移的重排序服务。
public class RoutingRerankService implements RerankService {

    private final ModelSelector modelSelector;
    private final ModelRoutingExecutor routingExecutor;
    private final Map<String, RerankClient> rerankClients;

    // 注入模型选择器、执行器和所有 Rerank 客户端。
    public RoutingRerankService(
            ModelSelector modelSelector,
            ModelRoutingExecutor routingExecutor,
            List<RerankClient> rerankClients
    ) {
        this.modelSelector = modelSelector;
        this.routingExecutor = routingExecutor;
        this.rerankClients = rerankClients.stream().collect(Collectors.toMap(RerankClient::provider, Function.identity()));
    }

    @Override
    // 执行重排序路由调用。
    public List<String> rerank(String query, List<String> candidates, int topN) {
        List<ModelTarget> targets = modelSelector.selectRerankCandidates(null);
        return routingExecutor.executeWithFallback(
                ModelCapability.RERANK,
                targets,
                target -> rerankClients.get(target.providerId()),
                (client, target) -> client.rerank(query, candidates, topN, target)
        );
    }
}
