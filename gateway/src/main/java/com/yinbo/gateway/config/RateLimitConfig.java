package com.yinbo.gateway.config;

import com.yinbo.gateway.rate.RateLimitIdentityResolver;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
// 频率限流相关配置。
public class RateLimitConfig {

    // 注册用户或 IP 维度的频率限流 key 解析器。
    @Bean
    public KeyResolver userOrIpKeyResolver(RateLimitIdentityResolver rateLimitIdentityResolver) {
        return rateLimitIdentityResolver::resolve;
    }

    // 创建读取 Spring Session 的 ReactiveRedisTemplate。
    @Bean
    public ReactiveRedisTemplate<String, Object> springSessionRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory
    ) {
        RedisSerializationContext<String, Object> context = RedisSerializationContext
                .<String, Object>newSerializationContext(new StringRedisSerializer())
                .hashKey(new StringRedisSerializer())
                .hashValue(new JdkSerializationRedisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
