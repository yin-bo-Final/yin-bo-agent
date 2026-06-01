package com.yinbo.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.concurrency")
// 资源并发限流配置属性。
public record ConcurrencyLimitProperties(
        Limit upload,
        Limit urlIngestion,
        Limit aiChat
) {

    // 给并发限流配置补默认值。
    public ConcurrencyLimitProperties {
        upload = Limit.withDefaults(upload, 10, Duration.ofMinutes(10));
        urlIngestion = Limit.withDefaults(urlIngestion, 5, Duration.ofMinutes(10));
        aiChat = Limit.withDefaults(aiChat, 20, Duration.ofMinutes(5));
    }

    // 单个资源的并发限流配置项。
    public record Limit(
            Integer maxPermits,
            Duration leaseTtl
    ) {

        // 规范化单个并发限流配置项。
        private static Limit withDefaults(Limit limit, int defaultMaxPermits, Duration defaultLeaseTtl) {
            if (limit == null) {
                return new Limit(defaultMaxPermits, defaultLeaseTtl);
            }
            Integer maxPermits = limit.maxPermits == null || limit.maxPermits <= 0
                    ? defaultMaxPermits
                    : limit.maxPermits;
            Duration leaseTtl = limit.leaseTtl == null || limit.leaseTtl.isZero() || limit.leaseTtl.isNegative()
                    ? defaultLeaseTtl
                    : limit.leaseTtl;
            return new Limit(maxPermits, leaseTtl);
        }
    }
}
