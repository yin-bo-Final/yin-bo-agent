package com.yinbo.agent.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
// RequestId 链路追踪过滤器。
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final String UNKNOWN = "-";

    private final long slowRequestThresholdMs;

    // 读取慢请求阈值配置。
    public RequestIdFilter(
            @Value("${app.logging.slow-request-threshold-ms:3000}") long slowRequestThresholdMs
    ) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    // 为每个请求生成或透传 requestId，并记录访问日志。
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        long startNanos = System.nanoTime();

        MDC.put(MDC_REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            boolean slow = costMs >= slowRequestThresholdMs;
            if (slow) {
                log.warn(
                        "event=access requestId={} method={} path={} status={} costMs={} slow={} clientIp={} userAgent={}",
                        requestId,
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        costMs,
                        true,
                        resolveClientIp(request),
                        sanitizeLogValue(request.getHeader("User-Agent"))
                );
            } else {
                log.info(
                        "event=access requestId={} method={} path={} status={} costMs={} slow={} clientIp={} userAgent={}",
                        requestId,
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        costMs,
                        false,
                        resolveClientIp(request),
                        sanitizeLogValue(request.getHeader("User-Agent"))
                );
            }
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
    }

    // 解析请求头中的 X-Request-Id。
    private static String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId) && SAFE_REQUEST_ID.matcher(requestId).matches()) {
            return requestId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 解析客户端 IP。
    private static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return sanitizeLogValue(forwardedFor.split(",", 2)[0].trim());
        }
        return sanitizeLogValue(request.getRemoteAddr());
    }

    // 清洗日志文本。
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
