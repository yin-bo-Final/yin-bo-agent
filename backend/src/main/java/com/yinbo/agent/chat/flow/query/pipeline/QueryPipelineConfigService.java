package com.yinbo.agent.chat.flow.query.pipeline;

import com.yinbo.agent.chat.entity.ChatPipelineConfig;
import com.yinbo.agent.chat.mapper.ChatPipelineConfigMapper;
import com.yinbo.agent.config.ChatQueryRewriteProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
// 查询预处理流水线配置服务。
public class QueryPipelineConfigService {

    private static final long CONFIG_ID = 1L;

    private final ChatPipelineConfigMapper configMapper;
    private final QueryPipelineConfigCacheService cacheService;
    private final ChatQueryRewriteProperties properties;

    // 注入配置 Mapper、Redis 缓存和默认属性。
    public QueryPipelineConfigService(
            ChatPipelineConfigMapper configMapper,
            QueryPipelineConfigCacheService cacheService,
            ChatQueryRewriteProperties properties
    ) {
        this.configMapper = configMapper;
        this.cacheService = cacheService;
        this.properties = properties;
    }

    // 查询当前流水线配置。
    public QueryPipelineConfigView currentConfig() {
        QueryPipelineConfigView cached = cacheService.get();
        if (cached != null) {
            return cached;
        }
        QueryPipelineConfigView config = toView(configMapper.selectById(CONFIG_ID));
        cacheService.put(config);
        return config;
    }

    @Transactional
    // 更新流水线配置。
    public QueryPipelineConfigView update(UpdateQueryPipelineConfigCommand command, Long updatedBy) {
        ChatPipelineConfig config = configMapper.selectById(CONFIG_ID);
        if (config == null) {
            config = defaultEntity();
        }
        if (command.terminologyEnabled() != null) {
            config.setTerminologyEnabled(command.terminologyEnabled());
        }
        if (command.llmRewriteEnabled() != null) {
            config.setLlmRewriteEnabled(command.llmRewriteEnabled());
        }
        if (command.ruleSplitEnabled() != null) {
            config.setRuleSplitEnabled(command.ruleSplitEnabled());
        }
        if (command.fallbackPolicy() != null && !command.fallbackPolicy().isBlank()) {
            config.setFallbackPolicy(normalizeFallbackPolicy(command.fallbackPolicy()));
        }
        if (command.rewriteTimeoutMs() != null && command.rewriteTimeoutMs() > 0) {
            config.setRewriteTimeoutMs(Math.min(command.rewriteTimeoutMs(), 30_000));
        }
        if (command.rewriteContextTurns() != null && command.rewriteContextTurns() > 0) {
            config.setRewriteContextTurns(Math.min(command.rewriteContextTurns(), 10));
        }
        config.setUpdatedBy(updatedBy);
        if (configMapper.selectById(CONFIG_ID) == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        QueryPipelineConfigView view = toView(config);
        runAfterCommit(cacheService::evict);
        return view;
    }

    private QueryPipelineConfigView toView(ChatPipelineConfig config) {
        ChatPipelineConfig safeConfig = config == null ? defaultEntity() : config;
        return new QueryPipelineConfigView(
                booleanOrDefault(safeConfig.getTerminologyEnabled(), properties.terminologyEnabled()),
                booleanOrDefault(safeConfig.getLlmRewriteEnabled(), properties.llmRewriteEnabled()),
                booleanOrDefault(safeConfig.getRuleSplitEnabled(), properties.ruleSplitEnabled()),
                normalizeFallbackPolicy(safeConfig.getFallbackPolicy()),
                positiveOrDefault(safeConfig.getRewriteTimeoutMs(), properties.rewriteTimeoutMs()),
                positiveOrDefault(safeConfig.getRewriteContextTurns(), properties.rewriteContextTurns()),
                safeConfig.getUpdatedAt()
        );
    }

    private ChatPipelineConfig defaultEntity() {
        ChatPipelineConfig config = new ChatPipelineConfig();
        config.setId(CONFIG_ID);
        config.setTerminologyEnabled(properties.terminologyEnabled());
        config.setLlmRewriteEnabled(properties.llmRewriteEnabled());
        config.setRuleSplitEnabled(properties.ruleSplitEnabled());
        config.setFallbackPolicy(normalizeFallbackPolicy(properties.fallbackPolicy()));
        config.setRewriteTimeoutMs(properties.rewriteTimeoutMs());
        config.setRewriteContextTurns(properties.rewriteContextTurns());
        return config;
    }

    private boolean booleanOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String normalizeFallbackPolicy(String value) {
        String policy = value == null || value.isBlank() ? properties.fallbackPolicy() : value.trim().toUpperCase();
        if ("RULE_SPLIT".equals(policy) || "TERM_ONLY".equals(policy) || "BYPASS".equals(policy)) {
            return policy;
        }
        return "TERM_ONLY";
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            // 事务提交后清理配置缓存。
            public void afterCommit() {
                action.run();
            }
        });
    }

    // 管理后台更新流水线配置命令。
    public record UpdateQueryPipelineConfigCommand(
            Boolean terminologyEnabled,
            Boolean llmRewriteEnabled,
            Boolean ruleSplitEnabled,
            String fallbackPolicy,
            Integer rewriteTimeoutMs,
            Integer rewriteContextTurns
    ) {
    }
}
