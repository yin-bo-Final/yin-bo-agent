package com.yinbo.ai.infra.chat;

import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.chat.StreamCallback;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.ai.infra.http.ModelClientException;
import com.yinbo.ai.infra.model.ModelTarget;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// OpenAI 兼容协议 Chat 客户端基类。
public abstract class AbstractOpenAiStyleChatClient implements ChatClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // 注入 JSON 工具并初始化 HTTP 客户端。
    protected AbstractOpenAiStyleChatClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    // 发起 OpenAI 兼容非流式 Chat 调用。
    public LLMResponse chat(LLMRequest request, ModelTarget target) {
        try {
            String body = objectMapper.writeValueAsString(requestBody(request, target, false));
            HttpResponse<String> response = httpClient.send(buildRequest(target, body), HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode(), response.body(), target);
            JsonNode root = objectMapper.readTree(response.body());
            String content = chatContent(root);
            return new LLMResponse(target.id(), content, usage(root));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelClientException("模型调用被中断", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelClientException("模型调用失败：" + exception.getMessage(), exception);
        }
    }

    @Override
    // 发起 OpenAI 兼容流式 Chat 调用。
    public LLMResponse streamChat(LLMRequest request, StreamCallback callback, ModelTarget target) {
        StringBuilder contentBuilder = new StringBuilder();
        LLMResponse.TokenUsage[] usageHolder = new LLMResponse.TokenUsage[1];
        try {
            String body = objectMapper.writeValueAsString(requestBody(request, target, true));
            HttpResponse<Stream<String>> response = httpClient.send(buildRequest(target, body), HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelClientException("模型调用失败，HTTP 状态码：" + response.statusCode());
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> handleStreamLine(line, callback, contentBuilder, usageHolder));
            }
            return new LLMResponse(target.id(), contentBuilder.toString(), usageHolder[0]);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelClientException("模型流式调用被中断", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelClientException("模型流式调用失败：" + exception.getMessage(), exception);
        }
    }

    // 构造 OpenAI 兼容请求体。
    protected Map<String, Object> requestBody(LLMRequest request, ModelTarget target, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", target.modelName());
        body.put("messages", request.messages().stream()
                .map(message -> Map.of(
                        "role", message.role(),
                        "content", message.content()
                ))
                .toList());
        body.put("stream", stream);
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
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
        throw new ModelClientException("模型调用失败 targetId=%s status=%d body=%s".formatted(target.id(), statusCode, truncate(body, 500)));
    }

    private void handleStreamLine(
            String line,
            StreamCallback callback,
            StringBuilder contentBuilder,
            LLMResponse.TokenUsage[] usageHolder
    ) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if ("[DONE]".equals(payload)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            LLMResponse.TokenUsage usage = usage(root);
            if (usage != null) {
                usageHolder[0] = usage;
            }
            String delta = streamDelta(root);
            if (delta == null || delta.isEmpty()) {
                return;
            }
            contentBuilder.append(delta);
            callback.onDelta(delta);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelClientException("模型流式响应解析失败", exception);
        }
    }

    private String chatContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode message = choices.get(0).path("message");
        return message.path("content").asText("");
    }

    private String streamDelta(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode choice = choices.get(0);
        JsonNode delta = choice.path("delta");
        if (!delta.isMissingNode()) {
            return delta.path("content").asText("");
        }
        return choice.path("message").path("content").asText("");
    }

    private LLMResponse.TokenUsage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        return new LLMResponse.TokenUsage(
                intOrNull(usage.path("prompt_tokens")),
                intOrNull(usage.path("completion_tokens")),
                intOrNull(usage.path("total_tokens"))
        );
    }

    private Integer intOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
