package com.yinbo.gateway.filter;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestIdGlobalFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");
    private static final String UNKNOWN = "-";

    private final long slowRequestThresholdMs;

    public RequestIdGlobalFilter(
            @Value("${app.logging.slow-request-threshold-ms:3000}") long slowRequestThresholdMs
    ) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = resolveRequestId(exchange.getRequest());
        long startNanos = System.nanoTime();

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, requestId))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request)
                .build();

        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });

        return chain.filter(mutatedExchange)
                .doOnError(throwable -> log.error(
                        "event=exception requestId={} type={} message={}",
                        requestId,
                        throwable.getClass().getSimpleName(),
                        sanitizeLogValue(throwable.getMessage()),
                        throwable
                ))
                .doFinally(signalType -> {
                    long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    HttpStatusCode statusCode = mutatedExchange.getResponse().getStatusCode();
                    int status = statusCode == null ? 0 : statusCode.value();
                    boolean slow = costMs >= slowRequestThresholdMs;

                    if (slow) {
                        log.warn(
                                "event=access requestId={} method={} path={} status={} costMs={} slow={} clientIp={} userAgent={}",
                                requestId,
                                request.getMethod(),
                                request.getURI().getRawPath(),
                                status,
                                costMs,
                                true,
                                resolveClientIp(request),
                                sanitizeLogValue(request.getHeaders().getFirst("User-Agent"))
                        );
                    } else {
                        log.info(
                                "event=access requestId={} method={} path={} status={} costMs={} slow={} clientIp={} userAgent={}",
                                requestId,
                                request.getMethod(),
                                request.getURI().getRawPath(),
                                status,
                                costMs,
                                false,
                                resolveClientIp(request),
                                sanitizeLogValue(request.getHeaders().getFirst("User-Agent"))
                        );
                    }
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String resolveRequestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId) && SAFE_REQUEST_ID.matcher(requestId).matches()) {
            return requestId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String resolveClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return sanitizeLogValue(forwardedFor.split(",", 2)[0].trim());
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN;
        }
        return sanitizeLogValue(remoteAddress.getAddress().getHostAddress());
    }

    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        if (sanitized.length() > 256) {
            return sanitized.substring(0, 256);
        }
        return sanitized;
    }
}
