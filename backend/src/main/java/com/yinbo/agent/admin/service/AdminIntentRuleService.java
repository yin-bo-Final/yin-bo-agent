package com.yinbo.agent.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.admin.dto.IntentRuleRequest;
import com.yinbo.agent.admin.dto.IntentRuleResponse;
import com.yinbo.agent.chat.entity.ChatIntentNode;
import com.yinbo.agent.chat.entity.ChatIntentRule;
import com.yinbo.agent.chat.flow.intent.IntentRuleService;
import com.yinbo.agent.chat.flow.intent.IntentTreeService;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentRule;
import com.yinbo.agent.chat.flow.intent.model.IntentRuleMatchMode;
import com.yinbo.agent.chat.flow.intent.model.IntentRuleType;
import com.yinbo.agent.chat.flow.intent.model.IntentTreeData;
import com.yinbo.agent.chat.mapper.ChatIntentNodeMapper;
import com.yinbo.agent.chat.mapper.ChatIntentRuleMapper;
import com.yinbo.agent.common.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
// 管理后台意图规则维护服务。
public class AdminIntentRuleService {

    private final ChatIntentRuleMapper ruleMapper;
    private final ChatIntentNodeMapper nodeMapper;
    private final IntentRuleService intentRuleService;
    private final IntentTreeService intentTreeService;
    private final ObjectMapper objectMapper;

    // 注入规则、节点、运行时规则服务、意图树服务和 JSON 工具。
    public AdminIntentRuleService(
            ChatIntentRuleMapper ruleMapper,
            ChatIntentNodeMapper nodeMapper,
            IntentRuleService intentRuleService,
            IntentTreeService intentTreeService,
            ObjectMapper objectMapper
    ) {
        this.ruleMapper = ruleMapper;
        this.nodeMapper = nodeMapper;
        this.intentRuleService = intentRuleService;
        this.intentTreeService = intentTreeService;
        this.objectMapper = objectMapper;
    }

    // 查询意图规则列表。
    public List<IntentRuleResponse> rules() {
        IntentTreeData treeData = intentTreeService.loadAdminTreeData();
        return ruleMapper.selectList(new LambdaQueryWrapper<ChatIntentRule>()
                        .orderByDesc(ChatIntentRule::getUpdatedAt)
                        .orderByDesc(ChatIntentRule::getId))
                .stream()
                .map(rule -> toResponse(rule, treeData))
                .toList();
    }

    @Transactional
    // 创建意图规则。
    public IntentRuleResponse create(IntentRuleRequest request) {
        ensureRuleCodeAvailable(request.ruleCode(), null);
        ChatIntentRule rule = new ChatIntentRule();
        applyRequest(rule, request, true);
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "规则编码已存在");
        }
        runAfterCommit(intentRuleService::evictCache);
        return toResponse(ruleMapper.selectById(rule.getId()), intentTreeService.loadAdminTreeData());
    }

    @Transactional
    // 修改意图规则。
    public IntentRuleResponse update(Long ruleId, IntentRuleRequest request) {
        ChatIntentRule rule = requireRule(ruleId);
        ensureRuleCodeAvailable(request.ruleCode(), ruleId);
        applyRequest(rule, request, false);
        ruleMapper.updateById(rule);
        runAfterCommit(intentRuleService::evictCache);
        return toResponse(rule, intentTreeService.loadAdminTreeData());
    }

    @Transactional
    // 启用或禁用意图规则。
    public IntentRuleResponse updateEnabled(Long ruleId, boolean enabled) {
        ChatIntentRule rule = requireRule(ruleId);
        rule.setEnabled(enabled);
        ruleMapper.updateById(rule);
        runAfterCommit(intentRuleService::evictCache);
        return toResponse(rule, intentTreeService.loadAdminTreeData());
    }

    @Transactional
    // 删除意图规则。
    public void delete(Long ruleId) {
        requireRule(ruleId);
        ruleMapper.deleteById(ruleId);
        runAfterCommit(intentRuleService::evictCache);
    }

    private void applyRequest(ChatIntentRule rule, IntentRuleRequest request, boolean creating) {
        IntentRuleType ruleType = IntentRuleType.from(request.ruleType());
        IntentRuleMatchMode includeMode = IntentRuleMatchMode.from(request.includeMatchMode());
        IntentRuleMatchMode requireMode = IntentRuleMatchMode.from(request.requireMatchMode());
        ChatIntentNode target = requireTargetNode(request.targetNodeCode());
        if (ruleType == IntentRuleType.STRONG && hasChildren(target.getNodeCode())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "强规则必须指向叶子节点");
        }
        List<String> includeKeywords = cleanKeywords(request.includeKeywords());
        List<String> requireKeywords = cleanKeywords(request.requireKeywords());
        if (includeKeywords.isEmpty() && requireKeywords.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "至少填写一组正向关键词");
        }

        rule.setRuleCode(normalizeText(request.ruleCode()));
        rule.setName(normalizeText(request.name()));
        rule.setDescription(blankToNull(request.description()));
        rule.setTargetNodeCode(normalizeText(request.targetNodeCode()));
        rule.setRuleType(ruleType.name());
        rule.setIncludeKeywordsJson(writeKeywords(includeKeywords));
        rule.setIncludeMatchMode(includeMode.name());
        rule.setRequireKeywordsJson(writeKeywords(requireKeywords));
        rule.setRequireMatchMode(requireMode.name());
        rule.setExcludeKeywordsJson(writeKeywords(cleanKeywords(request.excludeKeywords())));
        rule.setScore(scoreOrDefault(request.score()));
        rule.setEnabled(request.enabled() == null || request.enabled());
        if (creating && rule.getId() != null) {
            rule.setId(null);
        }
    }

    private IntentRuleResponse toResponse(ChatIntentRule rule, IntentTreeData treeData) {
        IntentRule runtimeRule = intentRuleService.toRuntimeRule(rule);
        IntentNode target = treeData.nodeById().get(runtimeRule.targetNodeCode());
        return new IntentRuleResponse(
                runtimeRule.id(),
                runtimeRule.ruleCode(),
                runtimeRule.name(),
                runtimeRule.description(),
                runtimeRule.targetNodeCode(),
                target == null ? null : target.getName(),
                target == null ? null : target.getFullPath(),
                runtimeRule.ruleType().name(),
                runtimeRule.includeKeywords(),
                runtimeRule.includeMatchMode().name(),
                runtimeRule.requireKeywords(),
                runtimeRule.requireMatchMode().name(),
                runtimeRule.excludeKeywords(),
                runtimeRule.score(),
                runtimeRule.enabled(),
                runtimeRule.createdAt(),
                runtimeRule.updatedAt()
        );
    }

    private ChatIntentRule requireRule(Long ruleId) {
        ChatIntentRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "意图规则不存在");
        }
        return rule;
    }

    private ChatIntentNode requireTargetNode(String targetNodeCode) {
        ChatIntentNode node = nodeMapper.selectOne(new LambdaQueryWrapper<ChatIntentNode>()
                .eq(ChatIntentNode::getNodeCode, normalizeText(targetNodeCode))
                .last("LIMIT 1"));
        if (node == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "目标意图节点不存在");
        }
        return node;
    }

    private boolean hasChildren(String nodeCode) {
        Long childCount = nodeMapper.selectCount(new LambdaQueryWrapper<ChatIntentNode>()
                .eq(ChatIntentNode::getParentCode, nodeCode));
        return childCount != null && childCount > 0;
    }

    private void ensureRuleCodeAvailable(String ruleCode, Long currentId) {
        ChatIntentRule existing = ruleMapper.selectOne(new LambdaQueryWrapper<ChatIntentRule>()
                .eq(ChatIntentRule::getRuleCode, normalizeText(ruleCode))
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "规则编码已存在");
        }
    }

    private List<String> cleanKeywords(List<String> keywords) {
        return keywords == null ? List.of() : keywords.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private String writeKeywords(List<String> keywords) {
        try {
            return objectMapper.writeValueAsString(keywords == null ? List.of() : keywords);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "关键词格式错误");
        }
    }

    private BigDecimal scoreOrDefault(Double score) {
        if (score == null) {
            return BigDecimal.valueOf(0.9D);
        }
        if (score < 0D || score > 1D) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规则分数必须在 0 到 1 之间");
        }
        return BigDecimal.valueOf(score);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            // 事务提交后清理意图规则缓存。
            public void afterCommit() {
                action.run();
            }
        });
    }
}
