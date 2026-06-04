package com.yinbo.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gateway.internal")
// Gateway 内部路由访问控制配置。
public record InternalRouteProperties(String token) {

    // 判断内部路由 token 是否已配置。
    public boolean tokenConfigured() {
        return token != null && !token.isBlank();
    }

    // 校验请求 token。
    public boolean matches(String requestToken) {
        return tokenConfigured() && token.equals(requestToken);
    }
}
