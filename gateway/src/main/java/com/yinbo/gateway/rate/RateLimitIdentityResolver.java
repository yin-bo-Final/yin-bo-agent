package com.yinbo.gateway.rate;

import com.yinbo.gateway.ip.ClientIpResolver;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
// 频率限流身份解析器。
public class RateLimitIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(RateLimitIdentityResolver.class);
    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final String LOGIN_USER_ID_HASH_KEY = "sessionAttr:LOGIN_USER_ID";

    private final ReactiveRedisTemplate<String, Object> springSessionRedisTemplate;
    private final ClientIpResolver clientIpResolver;
    private final String sessionNamespace;

    // 注入 Spring Session Redis 读取器、真实 IP 解析器和 session 命名空间。
    public RateLimitIdentityResolver(
            ReactiveRedisTemplate<String, Object> springSessionRedisTemplate,
            ClientIpResolver clientIpResolver,
            @Value("${app.session.redis.namespace:yinbo:agent:session}") String sessionNamespace
    ) {
        this.springSessionRedisTemplate = springSessionRedisTemplate;
        this.clientIpResolver = clientIpResolver;
        this.sessionNamespace = sessionNamespace;
    }

    // 解析当前请求的限流身份。
    public Mono<String> resolve(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String ipKey = "ip:" + clientIpResolver.resolve(request);
        String sessionId = resolveSessionId(request);
        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(ipKey);
        }

        // Spring Session 的真实 Redis key 由 namespace + sessions + sessionId 组成。
        String sessionKey = sessionNamespace + ":sessions:" + sessionId;
        return springSessionRedisTemplate.opsForHash()
                .get(sessionKey, LOGIN_USER_ID_HASH_KEY)
                .map(this::toUserKey)
                .filter(StringUtils::hasText)
                .defaultIfEmpty(ipKey)
                .onErrorResume(exception -> {
                    log.warn(
                            "event=rate_limit_identity_lookup_failed sessionId={} type={} message={}",
                            sanitizeLogValue(sessionId),
                            exception.getClass().getSimpleName(),
                            sanitizeLogValue(exception.getMessage())
                    );
                    return Mono.just(ipKey);
                });
    }

    // 把 Redis Session 中的用户 ID 转成 RedisRateLimiter 的限流 key。
    private String toUserKey(Object value) {
        if (value instanceof Number number) {
            return "user:" + number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return "user:" + sanitizeIdentity(text);
        }
        return null;
    }

    // 从请求 Cookie 中解析 Spring Session ID。
    private static String resolveSessionId(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(SESSION_COOKIE_NAME);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        String value = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        return sanitizeIdentity(value);
    }

    // 清洗限流身份字符串。
    private static String sanitizeIdentity(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9:.\\-]", "_");
    }

    // 清洗日志文本。
    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 128 ? sanitized : sanitized.substring(0, 128);
    }
}
