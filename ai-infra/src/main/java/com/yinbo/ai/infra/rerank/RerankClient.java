package com.yinbo.ai.infra.rerank;

import com.yinbo.ai.infra.model.ModelTarget;
import java.util.List;

// 具体供应商的 Rerank 客户端接口。
public interface RerankClient {

    // 返回供应商标识。
    String provider();

    // 对候选文本执行重排序。
    List<String> rerank(String query, List<String> candidates, int topN, ModelTarget target);
}
