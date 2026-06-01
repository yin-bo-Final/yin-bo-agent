package com.yinbo.gateway.ip;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gateway")
// Gateway 可信代理配置属性。
public record TrustedProxyProperties(
        List<String> trustedProxies
) {

    private static final List<String> DEFAULT_TRUSTED_PROXIES = List.of(
            "127.0.0.1/32",
            "::1/128",
            "172.16.0.0/12"
    );

    // 规范化可信代理网段配置。
    public TrustedProxyProperties {
        trustedProxies = trustedProxies == null || trustedProxies.isEmpty()
                ? DEFAULT_TRUSTED_PROXIES
                : List.copyOf(trustedProxies);
    }
}
