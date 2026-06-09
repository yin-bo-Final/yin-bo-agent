package com.yinbo.agent.chat.flow.query.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.config.ChatQueryRewriteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
// 会话流水线配置 Redis 缓存。
public class QueryPipelineConfigCacheService {

    private static final Logger log = LoggerFactory.getLogger(QueryPipelineConfigCacheService.class);
    private static final String CACHE_KEY = "yinbo:agent:chat:pipeline-config:v1";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatQueryRewriteProperties properties;

    // 注入 Redis、JSON 工具和配置。
    public QueryPipelineConfigCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            ChatQueryRewriteProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // 读取流水线配置缓存。
    public QueryPipelineConfigView get() {
        try {
            String value = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, QueryPipelineConfigView.class);
        } catch (JsonProcessingException exception) {
            log.warn("event=query_pipeline_config_cache_decode_failed key={}", CACHE_KEY, exception);
            evict();
            return null;
        } catch (RuntimeException exception) {
            log.warn("event=query_pipeline_config_cache_read_failed key={}", CACHE_KEY, exception);
            return null;
        }
    }

    // 写入流水线配置缓存。
    public void put(QueryPipelineConfigView config) {
        try {
            stringRedisTemplate.opsForValue().set(
                    CACHE_KEY,
                    objectMapper.writeValueAsString(config),
                    properties.pipelineConfigCacheTtl()
            );
        } catch (JsonProcessingException exception) {
            log.warn("event=query_pipeline_config_cache_encode_failed key={}", CACHE_KEY, exception);
        } catch (RuntimeException exception) {
            log.warn("event=query_pipeline_config_cache_write_failed key={}", CACHE_KEY, exception);
        }
    }

    // 删除流水线配置缓存。
    public void evict() {
        try {
            stringRedisTemplate.delete(CACHE_KEY);
        } catch (RuntimeException exception) {
            log.warn("event=query_pipeline_config_cache_evict_failed key={}", CACHE_KEY, exception);
        }
    }
}
