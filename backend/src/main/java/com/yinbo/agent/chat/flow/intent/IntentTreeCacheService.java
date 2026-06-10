package com.yinbo.agent.chat.flow.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.config.ChatIntentProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
// 意图树 Redis 快照缓存。
public class IntentTreeCacheService {

    private static final Logger log = LoggerFactory.getLogger(IntentTreeCacheService.class);
    private static final String CACHE_KEY = "yinbo:agent:chat:intent-tree:v1";
    private static final TypeReference<List<IntentNode>> ROOTS_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatIntentProperties properties;

    // 注入 Redis、JSON 工具和意图识别配置。
    public IntentTreeCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            ChatIntentProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // 读取启用意图树缓存。
    public List<IntentNode> get() {
        try {
            String value = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, ROOTS_TYPE);
        } catch (JsonProcessingException exception) {
            log.warn("event=intent_tree_cache_decode_failed key={}", CACHE_KEY, exception);
            evict();
            return null;
        } catch (RuntimeException exception) {
            log.warn("event=intent_tree_cache_read_failed key={}", CACHE_KEY, exception);
            return null;
        }
    }

    // 写入启用意图树缓存。
    public void put(List<IntentNode> roots) {
        try {
            stringRedisTemplate.opsForValue().set(
                    CACHE_KEY,
                    objectMapper.writeValueAsString(roots == null ? List.of() : roots),
                    properties.cacheTtl()
            );
        } catch (JsonProcessingException exception) {
            log.warn("event=intent_tree_cache_encode_failed key={}", CACHE_KEY, exception);
        } catch (RuntimeException exception) {
            log.warn("event=intent_tree_cache_write_failed key={}", CACHE_KEY, exception);
        }
    }

    // 删除意图树缓存。
    public void evict() {
        try {
            stringRedisTemplate.delete(CACHE_KEY);
        } catch (RuntimeException exception) {
            log.warn("event=intent_tree_cache_evict_failed key={}", CACHE_KEY, exception);
        }
    }
}
