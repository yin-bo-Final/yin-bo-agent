package com.yinbo.gateway.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.gateway.filter.RequestIdGlobalFilter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
// Gateway 统一错误响应写入器。
public class GatewayErrorResponseWriter {

    private static final String REQUEST_ID_HEADER = RequestIdGlobalFilter.REQUEST_ID_HEADER;
    private static final String UNKNOWN = "-";

    private final ObjectMapper objectMapper;

    // 注入 JSON 序列化器。
    public GatewayErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 使用当前 exchange 的响应对象写入统一错误响应。
    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        return write(exchange, exchange.getResponse(), status, message);
    }

    // 使用指定响应对象写入统一错误响应。
    public Mono<Void> write(
            ServerWebExchange exchange,
            ServerHttpResponse response,
            HttpStatus status,
            String message
    ) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        ServerHttpRequest request = exchange.getRequest();
        String requestId = requestId(request);
        byte[] body = serializeBody(Map.of(
                "status", status.value(),
                "message", message,
                "requestId", requestId,
                "path", request.getURI().getRawPath(),
                "timestamp", Instant.now().toString()
        ), status, message);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getHeaders().set(REQUEST_ID_HEADER, requestId);

        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    // 从请求头读取 requestId。
    public String requestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return StringUtils.hasText(requestId) ? sanitizeLogValue(requestId) : UNKNOWN;
    }

    // 序列化统一错误响应体。
    private byte[] serializeBody(Map<String, Object> body, HttpStatus status, String message) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException exception) {
            return ("{\"status\":" + status.value() + ",\"message\":\"" + escapeJson(message) + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    // 清洗日志文本。
    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    // 转义兜底 JSON 中的文本。
    private static String escapeJson(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
