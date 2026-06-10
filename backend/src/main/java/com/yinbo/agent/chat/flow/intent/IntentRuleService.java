package com.yinbo.agent.chat.flow.intent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.entity.ChatIntentRule;
import com.yinbo.agent.chat.flow.intent.model.IntentRule;
import com.yinbo.agent.chat.flow.intent.model.IntentRuleMatchMode;
import com.yinbo.agent.chat.flow.intent.model.IntentRuleType;
import com.yinbo.agent.chat.mapper.ChatIntentRuleMapper;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
// 意图规则加载服务。
public class IntentRuleService {

    private static final Logger log = LoggerFactory.getLogger(IntentRuleService.class);
    private static final TypeReference<List<String>> KEYWORDS_TYPE = new TypeReference<>() {
    };

    private final ChatIntentRuleMapper ruleMapper;
    private final IntentRuleCacheService cacheService;
    private final ObjectMapper objectMapper;

    // 注入规则 Mapper、缓存和 JSON 工具。
    public IntentRuleService(
            ChatIntentRuleMapper ruleMapper,
            IntentRuleCacheService cacheService,
            ObjectMapper objectMapper
    ) {
        this.ruleMapper = ruleMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    // 加载启用规则，优先读取 Redis 快照。
    public List<IntentRule> enabledRules() {
        List<IntentRule> cached = cacheService.get();
        if (cached != null) {
            return cached;
        }
        List<IntentRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<ChatIntentRule>()
                        .eq(ChatIntentRule::getEnabled, true)
                        .orderByDesc(ChatIntentRule::getScore)
                        .orderByAsc(ChatIntentRule::getId))
                .stream()
                .map(this::toRuntimeRule)
                .toList();
        cacheService.put(rules);
        return rules;
    }

    // 清理规则缓存。
    public void evictCache() {
        cacheService.evict();
    }

    // 转换数据库实体为运行时规则。
    public IntentRule toRuntimeRule(ChatIntentRule rule) {
        return new IntentRule(
                rule.getId() == null ? null : rule.getId().toString(),
                rule.getRuleCode(),
                rule.getName(),
                rule.getDescription(),
                rule.getTargetNodeCode(),
                IntentRuleType.from(rule.getRuleType()),
                parseKeywords(rule.getIncludeKeywordsJson()),
                IntentRuleMatchMode.from(rule.getIncludeMatchMode()),
                parseKeywords(rule.getRequireKeywordsJson()),
                IntentRuleMatchMode.from(rule.getRequireMatchMode()),
                parseKeywords(rule.getExcludeKeywordsJson()),
                scoreOrDefault(rule.getScore()),
                rule.getEnabled() == null || rule.getEnabled(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    private List<String> parseKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(keywordsJson, KEYWORDS_TYPE).stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (JsonProcessingException exception) {
            log.warn("event=intent_rule_keywords_decode_failed value={}", sanitizeLogValue(keywordsJson));
            return List.of();
        }
    }

    private double scoreOrDefault(BigDecimal score) {
        return score == null ? 0.9D : score.doubleValue();
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
