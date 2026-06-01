package com.yinbo.agent.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
// 会话消息 Redis 缓存服务。
public class ChatMessageCacheService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageCacheService.class);
    private static final String CACHE_KEY_PREFIX = "yinbo:agent:chat:messages:";
    private static final Duration MESSAGE_CACHE_TTL = Duration.ofMinutes(30);
    private static final long DELAYED_EVICT_MILLIS = 500L;
    private static final TypeReference<List<CachedChatMessage>> CACHED_MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // 注入 Redis 模板和 JSON 序列化器。
    public ChatMessageCacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // 从缓存读取消息，缓存缺失时回源并写回。
    public List<CachedChatMessage> getMessages(
            Long userId,
            Long conversationId,
            Supplier<List<ChatMessageEntity>> dbLoader
    ) {
        String cacheKey = cacheKey(userId, conversationId);
        List<CachedChatMessage> cachedMessages = readMessages(cacheKey);
        if (cachedMessages != null) {
            return cachedMessages;
        }

        List<CachedChatMessage> messages = dbLoader.get().stream()
                .map(CachedChatMessage::from)
                .toList();
        writeMessages(cacheKey, messages);
        return messages;
    }

    // 删除指定会话的消息缓存。
    public void evictMessages(Long userId, Long conversationId) {
        String cacheKey = cacheKey(userId, conversationId);
        deleteCache(cacheKey);
        CompletableFuture.delayedExecutor(DELAYED_EVICT_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> deleteCache(cacheKey));
    }

    // 写入指定会话的消息缓存。
    public void putMessages(Long userId, Long conversationId, List<CachedChatMessage> messages) {
        writeMessages(cacheKey(userId, conversationId), messages);
    }

    private List<CachedChatMessage> readMessages(String cacheKey) {
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedValue == null || cachedValue.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cachedValue, CACHED_MESSAGE_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            log.warn("Chat message cache decode failed. cacheKey={}", cacheKey, exception);
            deleteCache(cacheKey);
            return null;
        } catch (RuntimeException exception) {
            log.warn("Chat message cache read failed. cacheKey={}", cacheKey, exception);
            return null;
        }
    }

    private void writeMessages(String cacheKey, List<CachedChatMessage> messages) {
        try {
            String cacheValue = objectMapper.writeValueAsString(messages);
            stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, MESSAGE_CACHE_TTL);
        } catch (JsonProcessingException exception) {
            log.warn("Chat message cache encode failed. cacheKey={}", cacheKey, exception);
        } catch (RuntimeException exception) {
            log.warn("Chat message cache write failed. cacheKey={}", cacheKey, exception);
        }
    }

    private void deleteCache(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (RuntimeException exception) {
            log.warn("Chat message cache delete failed. cacheKey={}", cacheKey, exception);
        }
    }

    // 构建会话消息缓存 key。
    private String cacheKey(Long userId, Long conversationId) {
        return CACHE_KEY_PREFIX + "user:" + userId + ":conversation:" + conversationId;
    }

    // 缓存中的会话消息结构。
    public record CachedChatMessage(
            String role,
            String content,
            String modelId,
            Instant createdAt,
            Long responseDurationMs,
            Integer totalTokens
    ) {

        // 从数据库消息实体转换为缓存消息。
        public static CachedChatMessage from(ChatMessageEntity message) {
            return new CachedChatMessage(
                    message.getRole(),
                    message.getContent(),
                    message.getModelId(),
                    toInstant(message.getCreatedAt()),
                    message.getResponseDurationMs(),
                    message.getTotalTokens()
            );
        }

        private static Instant toInstant(LocalDateTime value) {
            return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
        }
    }
}
