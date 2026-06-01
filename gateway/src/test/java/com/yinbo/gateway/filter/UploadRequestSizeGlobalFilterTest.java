package com.yinbo.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.gateway.config.GatewayRequestSizeProperties;
import com.yinbo.gateway.ip.ClientIpResolver;
import com.yinbo.gateway.ip.TrustedProxyProperties;
import com.yinbo.gateway.response.GatewayErrorResponseWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import reactor.core.publisher.Mono;

class UploadRequestSizeGlobalFilterTest {

    // Content-Length 超过限制时直接返回 413。
    @Test
    void rejectsUploadWhenContentLengthExceedsLimit() {
        UploadRequestSizeGlobalFilter filter = uploadRequestSizeGlobalFilter(DataSize.ofBytes(10));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/ingestion/documents/upload")
                .header("Content-Length", "11")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("文件大小不能超过 10B");
    }

    // 非上传接口不执行文件大小限制。
    @Test
    void skipsNonUploadRequest() {
        UploadRequestSizeGlobalFilter filter = uploadRequestSizeGlobalFilter(DataSize.ofBytes(10));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/chat")
                .header("Content-Length", "11")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // 构造上传请求体大小限制过滤器。
    private static UploadRequestSizeGlobalFilter uploadRequestSizeGlobalFilter(DataSize uploadMaxSize) {
        return new UploadRequestSizeGlobalFilter(
                new GatewayRequestSizeProperties(uploadMaxSize),
                new GatewayErrorResponseWriter(new ObjectMapper()),
                new ClientIpResolver(new TrustedProxyProperties(List.of("127.0.0.1/32")))
        );
    }
}
