package com.yinbo.gateway.filter;

import com.yinbo.gateway.config.GatewayRequestSizeProperties;
import com.yinbo.gateway.ip.ClientIpResolver;
import com.yinbo.gateway.response.GatewayErrorResponseWriter;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
// 上传请求体大小限制全局过滤器。
public class UploadRequestSizeGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(UploadRequestSizeGlobalFilter.class);
    private static final String REQUEST_ID_HEADER = RequestIdGlobalFilter.REQUEST_ID_HEADER;
    private static final String UNKNOWN = "-";

    private final GatewayRequestSizeProperties requestSizeProperties;
    private final GatewayErrorResponseWriter errorResponseWriter;
    private final ClientIpResolver clientIpResolver;

    // 注入请求体大小配置、统一错误响应写入器和真实 IP 解析器。
    public UploadRequestSizeGlobalFilter(
            GatewayRequestSizeProperties requestSizeProperties,
            GatewayErrorResponseWriter errorResponseWriter,
            ClientIpResolver clientIpResolver
    ) {
        this.requestSizeProperties = requestSizeProperties;
        this.errorResponseWriter = errorResponseWriter;
        this.clientIpResolver = clientIpResolver;
    }

    // 对上传接口执行请求体大小限制。
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!isUploadRequest(request)) {
            return chain.filter(exchange);
        }

        long maxBytes = requestSizeProperties.uploadMaxSize().toBytes();
        long contentLength = request.getHeaders().getContentLength();
        if (contentLength > maxBytes) {
            return writeFileTooLargeResponse(exchange, contentLength, maxBytes);
        }

        ServerHttpRequest limitedRequest = request.mutate()
                .build();
        ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(limitedRequest) {
            private final AtomicLong receivedBytes = new AtomicLong();

            // 边读取请求体边统计大小，兼容没有 Content-Length 的上传请求。
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().handle((dataBuffer, sink) -> {
                    long totalBytes = receivedBytes.addAndGet(dataBuffer.readableByteCount());
                    if (totalBytes > maxBytes) {
                        DataBufferUtils.release(dataBuffer);
                        sink.error(new UploadRequestTooLargeException(totalBytes, maxBytes));
                        return;
                    }
                    sink.next(dataBuffer);
                });
            }
        };

        return chain.filter(exchange.mutate().request(decoratedRequest).build())
                .onErrorResume(
                        UploadRequestTooLargeException.class,
                        exception -> writeFileTooLargeResponse(exchange, exception.receivedBytes(), exception.maxBytes())
                );
    }

    // 声明上传请求体大小限制过滤器的执行顺序。
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    // 判断当前请求是否是文件上传接口。
    private boolean isUploadRequest(ServerHttpRequest request) {
        String path = request.getURI().getRawPath();
        return request.getMethod() == HttpMethod.POST
                && ("/api/ingestion/documents/upload".equals(path)
                || path.matches("^/api/admin/knowledge/bases/[^/]+/documents/upload$"));
    }

    // 写入文件过大响应。
    private Mono<Void> writeFileTooLargeResponse(ServerWebExchange exchange, long receivedBytes, long maxBytes) {
        ServerHttpRequest request = exchange.getRequest();
        log.warn(
                "event=upload_request_too_large requestId={} path={} clientIp={} receivedBytes={} maxBytes={}",
                requestId(request),
                request.getURI().getRawPath(),
                clientIpResolver.resolve(request),
                receivedBytes,
                maxBytes
        );
        return errorResponseWriter.write(
                exchange,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "文件大小不能超过 " + readableSize(maxBytes)
        );
    }

    // 从请求头读取 requestId。
    private static String requestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return StringUtils.hasText(requestId) ? sanitizeLogValue(requestId) : UNKNOWN;
    }

    // 清洗日志文本。
    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    // 格式化文件大小限制。
    private static String readableSize(long bytes) {
        long megabyte = 1024L * 1024L;
        if (bytes % megabyte == 0) {
            return (bytes / megabyte) + "MB";
        }
        return bytes + "B";
    }

    // 上传请求体过大异常。
    private static class UploadRequestTooLargeException extends RuntimeException {

        private final long receivedBytes;
        private final long maxBytes;

        // 创建请求体过大异常。
        private UploadRequestTooLargeException(long receivedBytes, long maxBytes) {
            this.receivedBytes = receivedBytes;
            this.maxBytes = maxBytes;
        }

        // 获取已接收字节数。
        private long receivedBytes() {
            return receivedBytes;
        }

        // 获取允许的最大字节数。
        private long maxBytes() {
            return maxBytes;
        }
    }
}
