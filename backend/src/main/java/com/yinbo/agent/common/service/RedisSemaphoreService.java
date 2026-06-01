package com.yinbo.agent.common.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    private final StringRedisTemplate stringRedisTemplate;

    // 注入 Redis 字符串模板。
    public RedisSemaphoreService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 尝试获取带租约时间的信号量许可。
    public Optional<Permit> tryAcquire(String name, int maxPermits, Duration leaseTtl) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("信号量名称不能为空");
        }
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("信号量许可数必须大于 0");
        }
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("信号量租约时间必须大于 0");
        }

        String key = KEY_PREFIX + name;
        String permitId = UUID.randomUUID().toString();
        long nowMillis = System.currentTimeMillis();
        long leaseMillis = leaseTtl.toMillis();
        Long acquired = stringRedisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(key),
                Long.toString(nowMillis),
                Long.toString(leaseMillis),
                Integer.toString(maxPermits),
                permitId,
                Long.toString(nowMillis + leaseMillis)
        );
        if (Long.valueOf(1L).equals(acquired)) {
            return Optional.of(new Permit(name, key, permitId));
        }
        return Optional.empty();
    }

    // 释放指定信号量许可。
    private void release(String name, String key, String permitId) {
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(key), permitId);
        } catch (RuntimeException exception) {
            log.warn(
                    "event=redis_semaphore_release_failed name={} type={} message={}",
                    name,
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
        }
    }

    // 自动释放的信号量许可。
    public final class Permit implements AutoCloseable {

        private final String name;
        private final String key;
        private final String permitId;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Permit(String name, String key, String permitId) {
            this.name = name;
            this.key = key;
            this.permitId = permitId;
        }

        @Override
        // 关闭许可并释放 Redis 中的占位。
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(name, key, permitId);
            }
        }
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
