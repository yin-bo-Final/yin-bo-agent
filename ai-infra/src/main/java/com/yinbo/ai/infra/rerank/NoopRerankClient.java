package com.yinbo.ai.infra.rerank;

import com.yinbo.ai.infra.model.ModelTarget;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
// 空实现重排序客户端，用于没有真实 Rerank 模型时兜底。
public class NoopRerankClient implements RerankClient {

    @Override
    // 返回空实现供应商标识。
    public String provider() {
        return "noop";
    }

    @Override
    // 原样返回前 topN 个候选文本。
    public List<String> rerank(String query, List<String> candidates, int topN, ModelTarget target) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int limit = topN <= 0 ? candidates.size() : Math.min(topN, candidates.size());
        return candidates.subList(0, limit);
    }
}
