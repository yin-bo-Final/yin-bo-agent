package com.yinbo.gateway.filter;

import com.yinbo.gateway.ip.ClientIpResolver;
import com.yinbo.gateway.response.GatewayErrorResponseWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
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

    private final GatewayErrorResponseWriter errorResponseWriter;
    private final ClientIpResolver clientIpResolver;

    // 注入统一错误响应写入器和真实 IP 解析器。
    public RateLimitResponseGlobalFilter(
            GatewayErrorResponseWriter errorResponseWriter,
            ClientIpResolver clientIpResolver
    ) {
        this.errorResponseWriter = errorResponseWriter;
        this.clientIpResolver = clientIpResolver;
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
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    // 写入统一的频率限流响应。
    private Mono<Void> writeRateLimitResponse(ServerHttpResponse response, ServerWebExchange exchange) {
        String requestId = requestId(exchange.getRequest());
        String routeId = routeId(exchange);
        String clientIp = clientIpResolver.resolve(exchange.getRequest());
        String path = exchange.getRequest().getURI().getRawPath();

        log.warn(
                "event=rate_limited requestId={} routeId={} path={} clientIp={}",
                requestId,
                routeId,
                path,
                clientIp
        );
        return errorResponseWriter.write(exchange, response, HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_MESSAGE);
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

    // 清洗日志文本。
    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
