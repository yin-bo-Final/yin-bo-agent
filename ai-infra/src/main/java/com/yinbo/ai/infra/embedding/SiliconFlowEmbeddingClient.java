package com.yinbo.ai.infra.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
// 硅基流动 Embedding 客户端。
public class SiliconFlowEmbeddingClient extends AbstractOpenAiStyleEmbeddingClient {

    public SiliconFlowEmbeddingClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String provider() {
        return "siliconflow";
    }
}
