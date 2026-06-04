package com.yinbo.ai.infra.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.ai.infra.http.ModelClientException;
import com.yinbo.ai.infra.model.ModelTarget;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

// OpenAI 兼容协议 Embedding 客户端基类。
public abstract class AbstractOpenAiStyleEmbeddingClient implements EmbeddingClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // 注入 JSON 工具并初始化 HTTP 客户端。
    protected AbstractOpenAiStyleEmbeddingClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    // 执行 OpenAI 兼容 Embedding 调用。
    public List<float[]> embedBatch(List<String> texts, ModelTarget target) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            String body = objectMapper.writeValueAsString(requestBody(texts, target));
            HttpResponse<String> response = httpClient.send(buildRequest(target, body), HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode(), response.body(), target);
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray()) {
                throw new ModelClientException("Embedding 响应缺少 data 数组");
            }
            return StreamSupport.stream(data.spliterator(), false)
                    .sorted(Comparator.comparingInt(node -> node.path("index").asInt(0)))
                    .map(this::embedding)
                    .toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelClientException("Embedding 调用被中断", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelClientException("Embedding 调用失败：" + exception.getMessage(), exception);
        }
    }

    // 构造 Embedding 请求体。
    protected Map<String, Object> requestBody(List<String> texts, ModelTarget target) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", target.modelName());
        body.put("input", texts);
        if (target.dimension() != null && target.dimension() > 0) {
            body.put("dimensions", target.dimension());
        }
        return body;
    }

    private HttpRequest buildRequest(ModelTarget target, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(target.requestUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        String apiKey = target.provider().apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private void requireSuccess(int statusCode, String body, ModelTarget target) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new ModelClientException("Embedding 调用失败 targetId=%s status=%d body=%s".formatted(target.id(), statusCode, truncate(body, 500)));
    }

    private float[] embedding(JsonNode node) {
        JsonNode embedding = node.path("embedding");
        if (!embedding.isArray()) {
            throw new ModelClientException("Embedding 响应缺少 embedding 数组");
        }
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = (float) embedding.get(i).asDouble();
        }
        return result;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
