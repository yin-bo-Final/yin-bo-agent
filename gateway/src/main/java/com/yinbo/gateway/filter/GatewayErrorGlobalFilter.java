package com.yinbo.gateway.filter;

import com.yinbo.gateway.response.GatewayErrorResponseWriter;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
// Gateway 异常统一响应全局过滤器。
public class GatewayErrorGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorGlobalFilter.class);

    private final GatewayErrorResponseWriter errorResponseWriter;

    // 注入统一错误响应写入器。
    public GatewayErrorGlobalFilter(GatewayErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    // 捕获 gateway 转发链路异常并写入统一 JSON 响应。
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(throwable -> writeGatewayErrorResponse(exchange, throwable));
    }

    // 声明异常兜底过滤器的执行顺序。
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    // 写入 gateway 异常响应。
    private Mono<Void> writeGatewayErrorResponse(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }

        ServerHttpRequest request = exchange.getRequest();
        HttpStatus status = resolveStatus(throwable);
        String message = resolveMessage(status);
        log.error(
                "event=gateway_error requestId={} path={} status={} type={} message={}",
                errorResponseWriter.requestId(request),
                request.getURI().getRawPath(),
                status.value(),
                throwable.getClass().getSimpleName(),
                sanitizeLogValue(throwable.getMessage()),
                throwable
        );
        return errorResponseWriter.write(exchange, status, message);
    }

    // 根据异常类型决定 gateway 响应状态码。
    private HttpStatus resolveStatus(Throwable throwable) {
        if (throwable instanceof ResponseStatusException exception
                && exception.getStatusCode() instanceof HttpStatus httpStatus) {
            return httpStatus;
        }
        if (containsCause(throwable, TimeoutException.class)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (containsCause(throwable, ConnectException.class) || containsCause(throwable, UnknownHostException.class)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    // 根据状态码决定对前端展示的错误信息。
    private String resolveMessage(HttpStatus status) {
        if (status == HttpStatus.GATEWAY_TIMEOUT) {
            return "服务响应超时，请稍后再试";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "服务暂时不可用，请稍后再试";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "请求路径不存在";
        }
        return "网关服务异常，请稍后再试";
    }

    // 判断异常链中是否包含指定异常类型。
    private static boolean containsCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // 清洗写入日志的异常文本。
    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
