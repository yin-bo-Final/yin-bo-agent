package com.yinbo.agent.infra.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.config.AiInfraProperties;
import com.yinbo.ai.api.chat.ChatStreamChunk;
import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.chat.StreamCallback;
import com.yinbo.ai.api.embedding.EmbeddingRequest;
import com.yinbo.ai.api.embedding.EmbeddingResponse;
import com.yinbo.ai.api.embedding.EmbeddingService;
import com.yinbo.ai.api.model.ModelOption;
import com.yinbo.ai.api.rerank.RerankRequest;
import com.yinbo.ai.api.rerank.RerankResponse;
import com.yinbo.ai.api.rerank.RerankService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
// backend 调用 ai-infra 的 HTTP 客户端。
public class AiInfraClient implements LLMService, EmbeddingService, RerankService {

    private final AiInfraProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // 注入远程服务配置和 JSON 工具。
    public AiInfraClient(AiInfraProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 查询 ai-infra 暴露的前端模型列表。
    public List<ModelOption> models() {
        return get("/internal/ai/models", new TypeReference<>() {
        });
    }

    // 解析请求模型，找不到时使用 ai-infra 返回的第一个模型兜底。
    public ModelOption resolveModel(String modelId) {
        String requestedModelId = modelId == null || modelId.isBlank() ? null : modelId.trim();
        try {
            List<ModelOption> models = models();
            return models.stream()
                    .filter(model -> model.id().equals(requestedModelId))
                    .findFirst()
                    .orElseGet(() -> models.isEmpty()
                            ? fallbackModel(requestedModelId)
                            : models.get(0));
        } catch (RuntimeException exception) {
            return fallbackModel(requestedModelId);
        }
    }

    private ModelOption fallbackModel(String requestedModelId) {
        String modelId = requestedModelId == null || requestedModelId.isBlank() ? "default" : requestedModelId;
        return new ModelOption(modelId, modelId, "ai-infra", true);
    }

    @Override
    // 远程执行非流式 Chat。
    public LLMResponse chat(LLMRequest request) {
        return post("/internal/ai/chat", request, LLMResponse.class);
    }

    @Override
    // 远程执行流式 Chat。
    public LLMResponse streamChat(LLMRequest request, StreamCallback callback) {
        String body = toJson(request);
        HttpRequest httpRequest = postRequest("/internal/ai/chat/stream", body)
                .header("Accept", "application/x-ndjson")
                .build();
        StringBuilder contentBuilder = new StringBuilder();
        LLMResponse[] completed = new LLMResponse[1];
        try {
            HttpResponse<Stream<String>> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            requireSuccess(response.statusCode(), "");
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> handleStreamLine(line, callback, contentBuilder, completed, request.modelId()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ai-infra 流式调用被中断", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("ai-infra 流式调用失败：" + exception.getMessage(), exception);
        }
        if (completed[0] != null) {
            return completed[0];
        }
        return new LLMResponse(request.modelId(), contentBuilder.toString(), null);
    }

    @Override
    // 远程批量生成 Embedding。
    public List<float[]> embedBatch(List<String> texts, String modelId) {
        EmbeddingResponse response = post("/internal/ai/embeddings", new EmbeddingRequest(modelId, texts), EmbeddingResponse.class);
        return toFloatArrays(response.embeddings());
    }

    @Override
    // 查询远程 Embedding 维度。
    public int dimension(String modelId) {
        EmbeddingResponse response = post("/internal/ai/embeddings", new EmbeddingRequest(modelId, List.of()), EmbeddingResponse.class);
        return response.dimension() == null || response.dimension() <= 0 ? 1024 : response.dimension();
    }

    @Override
    // 远程执行 Rerank。
    public List<String> rerank(String query, List<String> candidates, int topN) {
        RerankResponse response = post("/internal/ai/rerank", new RerankRequest(query, candidates, topN), RerankResponse.class);
        return response.results();
    }

    private void handleStreamLine(
            String line,
            StreamCallback callback,
            StringBuilder contentBuilder,
            LLMResponse[] completed,
            String fallbackModelId
    ) {
        if (line == null || line.isBlank()) {
            return;
        }
        ChatStreamChunk chunk = fromJson(line, ChatStreamChunk.class);
        if ("delta".equals(chunk.type())) {
            String delta = chunk.delta() == null ? "" : chunk.delta();
            if (!delta.isEmpty()) {
                contentBuilder.append(delta);
                callback.onDelta(delta);
            }
            return;
        }
        if ("done".equals(chunk.type())) {
            completed[0] = chunk.response() == null
                    ? new LLMResponse(fallbackModelId, contentBuilder.toString(), null)
                    : chunk.response();
            return;
        }
        if ("error".equals(chunk.type())) {
            throw new IllegalStateException(chunk.message() == null ? "ai-infra 流式调用失败" : chunk.message());
        }
    }

    private <T> T get(String path, TypeReference<T> typeReference) {
        HttpRequest request = withRequestId(HttpRequest.newBuilder(uri(path)))
                .timeout(properties.requestTimeout())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode(), response.body());
            return objectMapper.readValue(response.body(), typeReference);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ai-infra 调用被中断", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("ai-infra 调用失败：" + exception.getMessage(), exception);
        }
    }

    private <T> T post(String path, Object payload, Class<T> responseType) {
        HttpRequest request = postRequest(path, toJson(payload)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode(), response.body());
            return objectMapper.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ai-infra 调用被中断", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("ai-infra 调用失败：" + exception.getMessage(), exception);
        }
    }

    private HttpRequest.Builder postRequest(String path, String body) {
        return withRequestId(HttpRequest.newBuilder(uri(path)))
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder withRequestId(HttpRequest.Builder builder) {
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            builder.header("X-Request-Id", requestId);
        }
        return builder;
    }

    private URI uri(String path) {
        return URI.create(properties.baseUrl() + path);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("ai-infra 请求序列化失败", exception);
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("ai-infra 响应解析失败", exception);
        }
    }

    private List<float[]> toFloatArrays(List<List<Float>> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(vectors.size());
        for (List<Float> vector : vectors) {
            float[] values = new float[vector.size()];
            for (int index = 0; index < vector.size(); index++) {
                Float value = vector.get(index);
                values[index] = value == null ? 0F : value;
            }
            result.add(values);
        }
        return result;
    }

    private void requireSuccess(int statusCode, String body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new IllegalStateException("ai-infra 调用失败 status=" + statusCode + " body=" + truncate(body));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }
}
