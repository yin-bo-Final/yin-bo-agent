package com.yinbo.gateway.filter;

import com.yinbo.gateway.config.InternalRouteProperties;
import com.yinbo.gateway.ip.ClientIpResolver;
import com.yinbo.gateway.response.GatewayErrorResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
// Gateway 内部路由访问控制过滤器。
public class InternalRouteAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalRouteAuthGlobalFilter.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final InternalRouteProperties internalRouteProperties;
    private final GatewayErrorResponseWriter errorResponseWriter;
    private final ClientIpResolver clientIpResolver;

    // 注入内部路由配置、错误响应写入器和 IP 解析器。
    public InternalRouteAuthGlobalFilter(
            InternalRouteProperties internalRouteProperties,
            GatewayErrorResponseWriter errorResponseWriter,
            ClientIpResolver clientIpResolver
    ) {
        this.internalRouteProperties = internalRouteProperties;
        this.errorResponseWriter = errorResponseWriter;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    // 保护 /internal/** 路由，避免绕过 backend 鉴权和限流。
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();
        if (path == null || !path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }
        String requestToken = request.getHeaders().getFirst(INTERNAL_TOKEN_HEADER);
        if (internalRouteProperties.matches(requestToken)) {
            return chain.filter(exchange);
        }
        log.warn(
                "event=internal_route_rejected path={} clientIp={} tokenConfigured={}",
                path,
                clientIpResolver.resolve(request),
                internalRouteProperties.tokenConfigured()
        );
        return errorResponseWriter.write(exchange, HttpStatus.FORBIDDEN, "内部接口禁止直接访问");
    }

    @Override
    // 在路由转发前执行内部接口保护。
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
