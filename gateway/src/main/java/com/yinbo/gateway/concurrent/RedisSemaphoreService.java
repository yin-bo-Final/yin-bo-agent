package com.yinbo.gateway.concurrent;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
// Redis 分布式信号量服务。
public class RedisSemaphoreService {

    private static final Logger log = LoggerFactory.getLogger(RedisSemaphoreService.class);
    private static final String KEY_PREFIX = "yinbo:agent:semaphore:";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local current = redis.call('ZCARD', KEYS[1])
            if current < tonumber(ARGV[3]) then
                redis.call('ZADD', KEYS[1], ARGV[5], ARGV[4])
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
                return 1
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            if redis.call('ZCARD', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[1])
            end
            return removed
            """, Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    // 注入非阻塞 Redis 客户端。
    public RedisSemaphoreService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 尝试获取带租约时间的分布式信号量许可。
    public Mono<Permit> tryAcquire(String name, int maxPermits, Duration leaseTtl) {
        if (!StringUtils.hasText(name)) {
            return Mono.error(new IllegalArgumentException("信号量名称不能为空"));
        }
        if (maxPermits <= 0) {
            return Mono.error(new IllegalArgumentException("信号量许可数必须大于 0"));
        }
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            return Mono.error(new IllegalArgumentException("信号量租约时间必须大于 0"));
        }

        // Redis key 加统一前缀，避免和业务缓存、Spring Session key 混在一起。
        String key = KEY_PREFIX + name;
        String permitId = UUID.randomUUID().toString();
        long nowMillis = System.currentTimeMillis();
        long leaseMillis = leaseTtl.toMillis();

        // score 使用 expireAtMillis，让下一次获取许可时可以按时间清理过期占位。
        List<String> args = List.of(
                Long.toString(nowMillis),
                Long.toString(leaseMillis),
                Integer.toString(maxPermits),
                permitId,
                Long.toString(nowMillis + leaseMillis)
        );

        return redisTemplate.execute(ACQUIRE_SCRIPT, List.of(key), args)
                .next()
                .filter(result -> result == 1L)
                .map(result -> new Permit(name, key, permitId));
    }

    // 释放已经获取的分布式信号量许可。
    public Mono<Void> release(Permit permit) {
        if (permit == null) {
            return Mono.empty();
        }
        return redisTemplate.execute(RELEASE_SCRIPT, List.of(permit.key()), List.of(permit.permitId()))
                .next()
                .then()
                .onErrorResume(exception -> {
                    log.warn(
                            "event=redis_semaphore_release_failed name={} type={} message={}",
                            permit.name(),
                            exception.getClass().getSimpleName(),
                            sanitizeLogValue(exception.getMessage()),
                            exception
                    );
                    return Mono.empty();
                });
    }

    // Redis 信号量许可信息。
    public record Permit(
            String name,
            String key,
            String permitId
    ) {
    }

    // 清洗写入日志的文本。
    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
