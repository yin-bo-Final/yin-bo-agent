package com.yinbo.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.concurrency")
// 高成本任务并发限制配置。
public record ConcurrencyLimitProperties(
        Limit upload,
        Limit ingestion
) {

    // 给上传和入库消费并发配置补默认值。
    public ConcurrencyLimitProperties {
        upload = Limit.withDefaults(upload, 10, Duration.ofMinutes(10));
        ingestion = Limit.withDefaults(ingestion, 5, Duration.ofMinutes(30));
    }

    // 单个资源的并发限制配置项。
    public record Limit(
            Integer maxPermits,
            Duration leaseTtl
    ) {

        // 规范化单个并发限制配置项。
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
