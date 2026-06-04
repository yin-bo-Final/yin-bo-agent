package com.yinbo.ai.api.rerank;

import java.util.List;

// 重排序业务接口。
public interface RerankService {

    // 对候选文本执行重排序，返回重排后的文本。
    List<String> rerank(String query, List<String> candidates, int topN);
}
