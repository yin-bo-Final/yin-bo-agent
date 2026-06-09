package com.yinbo.agent.chat.flow.query.terminology;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.config.ChatQueryRewriteProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
// 术语字典 Redis 快照缓存服务。
public class TerminologyDictionaryCacheService {

    private static final Logger log = LoggerFactory.getLogger(TerminologyDictionaryCacheService.class);
    private static final String CACHE_KEY = "yinbo:agent:chat:terminology:enabled:v1";
    private static final TypeReference<List<TerminologyDictionaryEntry>> DICTIONARY_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatQueryRewriteProperties properties;

    // 注入 Redis、JSON 工具和查询改写配置。
    public TerminologyDictionaryCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            ChatQueryRewriteProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // 读取术语字典快照。
    public List<TerminologyDictionaryEntry> get() {
        try {
            String value = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, DICTIONARY_TYPE);
        } catch (JsonProcessingException exception) {
            log.warn("event=terminology_cache_decode_failed key={}", CACHE_KEY, exception);
            evict();
            return null;
        } catch (RuntimeException exception) {
            log.warn("event=terminology_cache_read_failed key={}", CACHE_KEY, exception);
            return null;
        }
    }

    // 写入术语字典快照。
    public void put(List<TerminologyDictionaryEntry> entries) {
        try {
            String value = objectMapper.writeValueAsString(entries == null ? List.of() : entries);
            stringRedisTemplate.opsForValue().set(CACHE_KEY, value, ttlWithJitter(properties.terminologyCacheTtl()));
        } catch (JsonProcessingException exception) {
            log.warn("event=terminology_cache_encode_failed key={}", CACHE_KEY, exception);
        } catch (RuntimeException exception) {
            log.warn("event=terminology_cache_write_failed key={}", CACHE_KEY, exception);
        }
    }

    // 删除术语字典快照。
    public void evict() {
        try {
            stringRedisTemplate.delete(CACHE_KEY);
        } catch (RuntimeException exception) {
            log.warn("event=terminology_cache_evict_failed key={}", CACHE_KEY, exception);
        }
    }

    private Duration ttlWithJitter(Duration ttl) {
        long seconds = Math.max(60L, ttl.toSeconds());
        long jitter = Math.max(1L, seconds / 10L);
        return Duration.ofSeconds(seconds + ThreadLocalRandom.current().nextLong(jitter + 1));
    }

    // 缓存中的术语标准词。
    public record TerminologyDictionaryEntry(
            Long termId,
            String canonicalName,
            String termType,
            Integer priority,
            List<TerminologyAliasEntry> aliases
    ) {

        // 规范化别名列表。
        public TerminologyDictionaryEntry {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    // 缓存中的术语别名。
    public record TerminologyAliasEntry(
            Long aliasId,
            String aliasName,
            String aliasNormalized,
            Integer priority
    ) {
    }
}
