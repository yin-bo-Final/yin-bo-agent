package com.yinbo.ai.infra.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.ai.api.chat.ChatStreamChunk;
import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.embedding.EmbeddingRequest;
import com.yinbo.ai.api.embedding.EmbeddingResponse;
import com.yinbo.ai.api.embedding.EmbeddingService;
import com.yinbo.ai.api.model.ModelOption;
import com.yinbo.ai.api.rerank.RerankRequest;
import com.yinbo.ai.api.rerank.RerankResponse;
import com.yinbo.ai.api.rerank.RerankService;
import com.yinbo.ai.infra.config.AiModelProperties;
import com.yinbo.ai.infra.http.ModelClientStreamClosedException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/internal/ai")
// AI 基础设施 HTTP 接口。
public class AiInfraController {

    private static final Logger log = LoggerFactory.getLogger(AiInfraController.class);
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    private final AiModelProperties aiModelProperties;
    private final LLMService llmService;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final ObjectMapper objectMapper;

    // 注入模型配置、三类模型能力服务和 JSON 工具。
    public AiInfraController(
            AiModelProperties aiModelProperties,
            LLMService llmService,
            EmbeddingService embeddingService,
            RerankService rerankService,
            ObjectMapper objectMapper
    ) {
        this.aiModelProperties = aiModelProperties;
        this.llmService = llmService;
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/models")
    // 查询前端可选择的 Chat 模型。
    public List<ModelOption> models() {
        return aiModelProperties.models().stream()
                .map(model -> new ModelOption(model.id(), model.name(), model.provider(), model.enabled()))
                .toList();
    }

    @PostMapping("/chat")
    // 执行非流式 Chat 调用。
    public LLMResponse chat(@RequestBody LLMRequest request) {
        return llmService.chat(request);
    }

    @PostMapping(value = "/chat/stream", produces = "application/x-ndjson")
    // 执行流式 Chat 调用，使用 NDJSON 把 delta 和 done 传给 backend。
    public StreamingResponseBody streamChat(@RequestBody LLMRequest request) {
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        return outputStream -> {
            bindRequestId(requestId);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            try {
                LLMResponse response = llmService.streamChat(request, delta -> writeChunk(writer, ChatStreamChunk.delta(delta)));
                writeChunk(writer, ChatStreamChunk.done(response));
            } catch (ModelClientStreamClosedException exception) {
                log.info("event=ai_stream_client_disconnected message={}", sanitizeLogValue(exception.getMessage()));
            } catch (RuntimeException exception) {
                try {
                    writeChunk(writer, ChatStreamChunk.error(sanitizeMessage(exception.getMessage())));
                } catch (ModelClientStreamClosedException disconnected) {
                    log.info("event=ai_stream_client_disconnected message={}", sanitizeLogValue(disconnected.getMessage()));
                }
            } finally {
                clearRequestId(requestId);
            }
        };
    }

    @PostMapping("/embeddings")
    // 批量生成 Embedding 向量。
    public EmbeddingResponse embeddings(@RequestBody EmbeddingRequest request) {
        List<float[]> vectors = embeddingService.embedBatch(request.texts(), request.modelId());
        return new EmbeddingResponse(toLists(vectors), embeddingService.dimension(request.modelId()));
    }

    @PostMapping("/rerank")
    // 执行 Rerank 调用。
    public RerankResponse rerank(@RequestBody RerankRequest request) {
        return new RerankResponse(rerankService.rerank(request.query(), request.candidates(), request.topN()));
    }

    private void writeChunk(BufferedWriter writer, ChatStreamChunk chunk) {
        try {
            writer.write(objectMapper.writeValueAsString(chunk));
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            throw new ModelClientStreamClosedException("客户端已断开 ai-infra 流式连接", exception);
        }
    }

    private List<List<Float>> toLists(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return List.of();
        }
        return vectors.stream()
                .map(this::toList)
                .toList();
    }

    private List<Float> toList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "模型调用失败";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    // 将 requestId 复制到流式响应异步线程。
    private void bindRequestId(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
        }
    }

    // 清理流式响应异步线程中的 requestId。
    private void clearRequestId(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
    }
}
