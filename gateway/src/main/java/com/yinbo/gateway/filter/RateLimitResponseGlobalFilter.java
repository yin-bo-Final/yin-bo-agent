package com.yinbo.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
// 频率限流响应全局过滤器。
public class RateLimitResponseGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitResponseGlobalFilter.class);
    private static final String REQUEST_ID_HEADER = RequestIdGlobalFilter.REQUEST_ID_HEADER;
    private static final String UNKNOWN = "-";
    private static final String RATE_LIMIT_MESSAGE = "请求过于频繁，请稍后再试";

    private final ObjectMapper objectMapper;

    // 注入 JSON 序列化器。
    public RateLimitResponseGlobalFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 拦截 RedisRateLimiter 产生的 429 响应并改写响应体。
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        AtomicBoolean responseWritten = new AtomicBoolean(false);
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            // 在响应完成前处理 429。
            @Override
            public Mono<Void> setComplete() {
                if (getStatusCode() == HttpStatus.TOO_MANY_REQUESTS && responseWritten.compareAndSet(false, true)) {
                    return writeRateLimitResponse(this, exchange);
                }
                return super.setComplete();
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    // 声明频率限流响应过滤器的执行顺序。
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    // 写入统一的频率限流响应。
    private Mono<Void> writeRateLimitResponse(ServerHttpResponse response, ServerWebExchange exchange) {
        String requestId = requestId(exchange.getRequest());
        String routeId = routeId(exchange);
        String clientIp = resolveClientIp(exchange.getRequest());
        String path = exchange.getRequest().getURI().getRawPath();

        // no-store 避免浏览器或代理缓存限流响应。
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getHeaders().set(REQUEST_ID_HEADER, requestId);

        log.warn(
                "event=rate_limited requestId={} routeId={} path={} clientIp={}",
                requestId,
                routeId,
                path,
                clientIp
        );

        byte[] body = serializeBody(Map.of(
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "message", RATE_LIMIT_MESSAGE,
                "requestId", requestId,
                "path", path,
                "timestamp", Instant.now().toString()
        ));
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    // 序列化频率限流响应体。
    private byte[] serializeBody(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException exception) {
            return ("{\"status\":429,\"message\":\"" + RATE_LIMIT_MESSAGE + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    // 从请求头读取 requestId。
    private static String requestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return StringUtils.hasText(requestId) ? sanitizeLogValue(requestId) : UNKNOWN;
    }

    // 读取当前命中的路由 ID。
    private static String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? UNKNOWN : sanitizeLogValue(route.getId());
    }

    // 解析客户端 IP。
    private static String resolveClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return sanitizeLogValue(forwardedFor.split(",", 2)[0].trim());
        }

        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return sanitizeLogValue(realIp);
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN;
        }
        return sanitizeLogValue(remoteAddress.getAddress().getHostAddress());
    }

    // 清洗日志文本。
    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
