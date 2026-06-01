package com.yinbo.gateway.filter;

import com.yinbo.gateway.concurrent.RedisSemaphoreService;
import com.yinbo.gateway.config.ConcurrencyLimitProperties;
import com.yinbo.gateway.response.GatewayErrorResponseWriter;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
// 资源并发限流全局过滤器。
public class ResourceConcurrencyGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ResourceConcurrencyGlobalFilter.class);
    private static final String REQUEST_ID_HEADER = RequestIdGlobalFilter.REQUEST_ID_HEADER;
    private static final String UNKNOWN = "-";

    private final ConcurrencyLimitProperties concurrencyLimitProperties;
    private final RedisSemaphoreService redisSemaphoreService;
    private final GatewayErrorResponseWriter errorResponseWriter;

    // 注入资源并发限流所需依赖。
    public ResourceConcurrencyGlobalFilter(
            ConcurrencyLimitProperties concurrencyLimitProperties,
            RedisSemaphoreService redisSemaphoreService,
            GatewayErrorResponseWriter errorResponseWriter
    ) {
        this.concurrencyLimitProperties = concurrencyLimitProperties;
        this.redisSemaphoreService = redisSemaphoreService;
        this.errorResponseWriter = errorResponseWriter;
    }

    // 对上传、URL 入库和 AI 对话执行 gateway 层并发限流。
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ResourceLimit resourceLimit = resolveResourceLimit(exchange.getRequest());
        if (resourceLimit == null) {
            return chain.filter(exchange);
        }

        long startedAt = System.nanoTime();

        // usingWhen 负责许可生命周期，覆盖正常完成、异常和客户端取消三种结束方式。
        return redisSemaphoreService.tryAcquire(
                        resourceLimit.semaphoreName(),
                        resourceLimit.limit().maxPermits(),
                        resourceLimit.limit().leaseTtl()
                )
                .onErrorMap(ResourceConcurrencyUnavailableException::new)
                .flatMap(permit -> Mono.usingWhen(
                        Mono.just(permit),
                        ignored -> chain.filter(exchange)
                                .doFinally(signalType -> logCompleted(exchange, resourceLimit, startedAt, signalType)),
                        redisSemaphoreService::release,
                        (ignored, throwable) -> redisSemaphoreService.release(permit),
                        redisSemaphoreService::release
                ))
                .switchIfEmpty(Mono.defer(() -> writeLimitedResponse(exchange, resourceLimit)))
                .onErrorResume(
                        ResourceConcurrencyUnavailableException.class,
                        exception -> writeUnavailableResponse(exchange, resourceLimit, exception.getCause())
                );
    }

    // 声明资源并发限流过滤器的执行顺序。
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    // 识别当前请求对应的高成本资源。
    private ResourceLimit resolveResourceLimit(ServerHttpRequest request) {
        String path = request.getURI().getRawPath();
        if (request.getMethod() == HttpMethod.POST
                && ("/api/ingestion/documents/upload".equals(path)
                || path.matches("^/api/admin/knowledge/bases/[^/]+/documents/upload$"))) {
            return new ResourceLimit(
                    "upload",
                    "gateway:ingestion:upload:global",
                    concurrencyLimitProperties.upload(),
                    "当前上传任务较多，请稍后再试",
                    "上传并发控制暂时不可用，请稍后再试"
            );
        }
        if (request.getMethod() == HttpMethod.POST
                && ("/api/ingestion/documents/url".equals(path)
                || path.matches("^/api/admin/knowledge/bases/[^/]+/documents/url$"))) {
            return new ResourceLimit(
                    "url_ingestion",
                    "gateway:ingestion:url:global",
                    concurrencyLimitProperties.urlIngestion(),
                    "当前 URL 入库任务较多，请稍后再试",
                    "URL 入库并发控制暂时不可用，请稍后再试"
            );
        }
        if (request.getMethod() == HttpMethod.POST
                && ("/api/chat".equals(path) || "/api/chat/stream".equals(path))) {
            return new ResourceLimit(
                    "ai_chat",
                    "gateway:ai:chat:global",
                    concurrencyLimitProperties.aiChat(),
                    "当前 AI 对话任务较多，请稍后再试",
                    "AI 对话并发控制暂时不可用，请稍后再试"
            );
        }
        return null;
    }

    // 写入资源并发超限响应。
    private Mono<Void> writeLimitedResponse(ServerWebExchange exchange, ResourceLimit resourceLimit) {
        ServerHttpRequest request = exchange.getRequest();
        log.warn(
                "event=resource_concurrency_limited resource={} requestId={} path={} clientIp={} maxPermits={}",
                resourceLimit.resource(),
                requestId(request),
                request.getURI().getRawPath(),
                resolveClientIp(request),
                resourceLimit.limit().maxPermits()
        );
        return errorResponseWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, resourceLimit.limitedMessage());
    }

    // 写入资源并发控制不可用响应。
    private Mono<Void> writeUnavailableResponse(
            ServerWebExchange exchange,
            ResourceLimit resourceLimit,
            Throwable exception
    ) {
        ServerHttpRequest request = exchange.getRequest();
        log.error(
                "event=resource_concurrency_unavailable resource={} requestId={} path={} clientIp={} maxPermits={} type={} message={}",
                resourceLimit.resource(),
                requestId(request),
                request.getURI().getRawPath(),
                resolveClientIp(request),
                resourceLimit.limit().maxPermits(),
                exception.getClass().getSimpleName(),
                sanitizeLogValue(exception.getMessage()),
                exception
        );
        return errorResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, resourceLimit.unavailableMessage());
    }

    // 记录资源并发许可使用完成日志。
    private void logCompleted(
            ServerWebExchange exchange,
            ResourceLimit resourceLimit,
            long startedAt,
            SignalType signalType
    ) {
        ServerHttpRequest request = exchange.getRequest();
        log.info(
                "event=resource_concurrency_completed resource={} requestId={} path={} status={} clientIp={} maxPermits={} signal={} costMs={}",
                resourceLimit.resource(),
                requestId(request),
                request.getURI().getRawPath(),
                exchange.getResponse().getStatusCode() == null ? 0 : exchange.getResponse().getStatusCode().value(),
                resolveClientIp(request),
                resourceLimit.limit().maxPermits(),
                signalType.name(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        );
    }

    // 从请求头读取 requestId。
    private static String requestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return StringUtils.hasText(requestId) ? sanitizeLogValue(requestId) : UNKNOWN;
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

    // 资源并发限流规则。
    private record ResourceLimit(
            String resource,
            String semaphoreName,
            ConcurrencyLimitProperties.Limit limit,
            String limitedMessage,
            String unavailableMessage
    ) {
    }

    private static class ResourceConcurrencyUnavailableException extends RuntimeException {

        // 包装资源并发控制异常。
        private ResourceConcurrencyUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
