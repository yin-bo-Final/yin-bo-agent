package com.yinbo.agent.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.admin.dto.IntentNodeRequest;
import com.yinbo.agent.admin.dto.IntentNodeResponse;
import com.yinbo.agent.chat.entity.ChatIntentNode;
import com.yinbo.agent.chat.entity.ChatIntentRule;
import com.yinbo.agent.chat.flow.intent.IntentTreeService;
import com.yinbo.agent.chat.flow.intent.model.IntentKind;
import com.yinbo.agent.chat.flow.intent.model.IntentLevel;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentTreeData;
import com.yinbo.agent.chat.mapper.ChatIntentNodeMapper;
import com.yinbo.agent.chat.mapper.ChatIntentRuleMapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.knowledge.entity.KnowledgeBase;
import com.yinbo.agent.knowledge.mapper.KnowledgeBaseMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
// 管理后台意图树维护服务。
public class AdminIntentService {

    private final ChatIntentNodeMapper intentNodeMapper;
    private final ChatIntentRuleMapper intentRuleMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final IntentTreeService intentTreeService;
    private final ObjectMapper objectMapper;

    // 注入意图节点、知识库、树服务和 JSON 工具。
    public AdminIntentService(
            ChatIntentNodeMapper intentNodeMapper,
            ChatIntentRuleMapper intentRuleMapper,
            KnowledgeBaseMapper knowledgeBaseMapper,
            IntentTreeService intentTreeService,
            ObjectMapper objectMapper
    ) {
        this.intentNodeMapper = intentNodeMapper;
        this.intentRuleMapper = intentRuleMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.intentTreeService = intentTreeService;
        this.objectMapper = objectMapper;
    }

    // 查询意图树结构。
    public List<IntentNodeResponse> tree() {
        return intentTreeService.loadAdminTreeData().roots().stream()
                .map(this::toResponse)
                .toList();
    }

    // 查询扁平意图节点列表。
    public List<IntentNodeResponse> nodes() {
        IntentTreeData treeData = intentTreeService.loadAdminTreeData();
        return treeData.allNodes().stream()
                .map(node -> toResponse(node, false))
                .toList();
    }

    @Transactional
    // 创建意图节点。
    public IntentNodeResponse create(IntentNodeRequest request) {
        ensureNodeCodeAvailable(request.nodeCode(), null);
        ChatIntentNode node = new ChatIntentNode();
        applyRequest(node, request, true);
        try {
            intentNodeMapper.insert(node);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "节点编码已存在");
        }
        runAfterCommit(intentTreeService::evictCache);
        return findResponseByDbId(node.getId());
    }

    @Transactional
    // 修改意图节点。
    public IntentNodeResponse update(Long nodeId, IntentNodeRequest request) {
        ChatIntentNode node = requireNode(nodeId);
        ensureNodeCodeAvailable(request.nodeCode(), nodeId);
        validateUpdateMove(node, request);
        applyRequest(node, request, false);
        intentNodeMapper.updateById(node);
        runAfterCommit(intentTreeService::evictCache);
        return findResponseByDbId(node.getId());
    }

    @Transactional
    // 更新启用状态，禁用父节点时递归禁用子节点。
    public IntentNodeResponse updateEnabled(Long nodeId, boolean enabled) {
        ChatIntentNode node = requireNode(nodeId);
        if (enabled && node.getParentCode() != null && !node.getParentCode().isBlank()) {
            ChatIntentNode parent = findByCode(node.getParentCode());
            if (parent != null && !Boolean.TRUE.equals(parent.getEnabled())) {
                throw new BusinessException(HttpStatus.CONFLICT, "请先启用父节点");
            }
        }
        if (enabled) {
            node.setEnabled(true);
            intentNodeMapper.updateById(node);
        } else {
            for (ChatIntentNode target : selfAndDescendants(node)) {
                target.setEnabled(false);
                intentNodeMapper.updateById(target);
            }
        }
        runAfterCommit(intentTreeService::evictCache);
        return findResponseByDbId(node.getId());
    }

    @Transactional
    // 删除意图节点，有子节点时拒绝删除。
    public void delete(Long nodeId) {
        ChatIntentNode node = requireNode(nodeId);
        Long childCount = intentNodeMapper.selectCount(new LambdaQueryWrapper<ChatIntentNode>()
                .eq(ChatIntentNode::getParentCode, node.getNodeCode()));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "请先删除子节点");
        }
        if (hasReferencingRules(node.getNodeCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "存在规则引用时不能删除节点，请先调整或删除规则");
        }
        intentNodeMapper.deleteById(nodeId);
        runAfterCommit(intentTreeService::evictCache);
    }

    private void applyRequest(ChatIntentNode node, IntentNodeRequest request, boolean creating) {
        IntentLevel level = IntentLevel.from(request.level());
        IntentKind kind = IntentKind.from(request.kind());
        String nodeCode = normalizeCode(request.nodeCode());
        String parentCode = blankToNull(request.parentCode());
        if (nodeCode.equals(parentCode)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "父节点不能指向自己");
        }
        if (parentCode != null && findByCode(parentCode) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "父节点不存在");
        }

        node.setNodeCode(nodeCode);
        node.setParentCode(parentCode);
        node.setName(normalizeText(request.name()));
        node.setDescription(blankToNull(request.description()));
        node.setLevel(level.name());
        node.setKind(kind.name());
        node.setExamplesJson(writeExamples(request.examples()));
        node.setKnowledgeBaseNo(blankToNull(request.knowledgeBaseNo()));
        node.setCollectionName(resolveCollectionName(kind, request.knowledgeBaseNo(), request.collectionName()));
        node.setMcpToolId(blankToNull(request.mcpToolId()));
        node.setPromptSnippet(blankToNull(request.promptSnippet()));
        node.setPromptTemplate(blankToNull(request.promptTemplate()));
        node.setParamPromptTemplate(blankToNull(request.paramPromptTemplate()));
        node.setTopK(positiveOrNull(request.topK()));
        node.setMinScore(scoreOrNull(request.minScore()));
        node.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        node.setEnabled(request.enabled() == null || request.enabled());
        if (creating && node.getId() != null) {
            node.setId(null);
        }
    }

    private void validateUpdateMove(ChatIntentNode current, IntentNodeRequest request) {
        String nextCode = normalizeCode(request.nodeCode());
        if (!current.getNodeCode().equals(nextCode)) {
            if (hasChildren(current.getNodeCode())) {
                throw new BusinessException(HttpStatus.CONFLICT, "存在子节点时不能修改节点编码");
            }
            if (hasReferencingRules(current.getNodeCode())) {
                throw new BusinessException(HttpStatus.CONFLICT, "存在规则引用时不能修改节点编码，请先调整或删除规则");
            }
        }
        String nextParentCode = blankToNull(request.parentCode());
        if (nextParentCode != null && isDescendantCode(nextParentCode, current.getNodeCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "父节点不能移动到自己的子树下面");
        }
    }

    private boolean hasChildren(String nodeCode) {
        Long childCount = intentNodeMapper.selectCount(new LambdaQueryWrapper<ChatIntentNode>()
                .eq(ChatIntentNode::getParentCode, nodeCode));
        return childCount != null && childCount > 0;
    }

    private boolean hasReferencingRules(String nodeCode) {
        Long ruleCount = intentRuleMapper.selectCount(new LambdaQueryWrapper<ChatIntentRule>()
                .eq(ChatIntentRule::getTargetNodeCode, nodeCode));
        return ruleCount != null && ruleCount > 0;
    }

    private boolean isDescendantCode(String possibleDescendantCode, String rootCode) {
        if (possibleDescendantCode == null || rootCode == null || possibleDescendantCode.equals(rootCode)) {
            return true;
        }
        List<ChatIntentNode> all = intentNodeMapper.selectList(new LambdaQueryWrapper<ChatIntentNode>());
        Set<String> descendants = new HashSet<>();
        descendants.add(rootCode);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ChatIntentNode node : all) {
                if (node.getParentCode() != null
                        && descendants.contains(node.getParentCode())
                        && descendants.add(node.getNodeCode())) {
                    changed = true;
                }
            }
        }
        return descendants.contains(possibleDescendantCode);
    }

    private String resolveCollectionName(IntentKind kind, String knowledgeBaseNo, String requestedCollection) {
        String cleanCollection = blankToNull(requestedCollection);
        if (kind != IntentKind.KB) {
            return cleanCollection;
        }
        String cleanKnowledgeBaseNo = blankToNull(knowledgeBaseNo);
        if (cleanKnowledgeBaseNo == null) {
            return cleanCollection;
        }
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getKnowledgeBaseNo, cleanKnowledgeBaseNo)
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "绑定的知识库不存在");
        }
        return blankToNull(knowledgeBase.getCollectionName());
    }

    private List<ChatIntentNode> selfAndDescendants(ChatIntentNode node) {
        List<ChatIntentNode> all = intentNodeMapper.selectList(new LambdaQueryWrapper<ChatIntentNode>());
        List<ChatIntentNode> result = new ArrayList<>();
        Set<String> selectedCodes = new HashSet<>();
        selectedCodes.add(node.getNodeCode());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ChatIntentNode candidate : all) {
                if (selectedCodes.contains(candidate.getNodeCode()) && !result.contains(candidate)) {
                    result.add(candidate);
                }
                if (candidate.getParentCode() != null
                        && selectedCodes.contains(candidate.getParentCode())
                        && selectedCodes.add(candidate.getNodeCode())) {
                    changed = true;
                }
            }
        }
        return result;
    }

    private IntentNodeResponse findResponseByDbId(Long dbId) {
        IntentTreeData data = intentTreeService.loadAdminTreeData();
        return data.allNodes().stream()
                .filter(node -> dbId != null && dbId.toString().equals(node.getDbId()))
                .findFirst()
                .map(node -> toResponse(node, false))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "意图节点不存在"));
    }

    private ChatIntentNode requireNode(Long nodeId) {
        ChatIntentNode node = intentNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "意图节点不存在");
        }
        return node;
    }

    private ChatIntentNode findByCode(String nodeCode) {
        if (nodeCode == null || nodeCode.isBlank()) {
            return null;
        }
        return intentNodeMapper.selectOne(new LambdaQueryWrapper<ChatIntentNode>()
                .eq(ChatIntentNode::getNodeCode, normalizeCode(nodeCode))
                .last("LIMIT 1"));
    }

    private void ensureNodeCodeAvailable(String nodeCode, Long currentId) {
        ChatIntentNode existing = findByCode(nodeCode);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "节点编码已存在");
        }
    }

    private IntentNodeResponse toResponse(IntentNode node) {
        return toResponse(node, true);
    }

    private IntentNodeResponse toResponse(IntentNode node, boolean withChildren) {
        List<IntentNodeResponse> children = withChildren
                ? node.getChildren().stream().map(this::toResponse).toList()
                : List.of();
        return new IntentNodeResponse(
                node.getDbId(),
                node.getId(),
                node.getParentId(),
                node.getName(),
                node.getDescription(),
                node.getLevel() == null ? null : node.getLevel().name(),
                node.getKind() == null ? null : node.getKind().name(),
                node.getExamples(),
                node.getFullPath(),
                node.isLeaf(),
                node.getKnowledgeBaseNo(),
                node.getCollectionName(),
                node.getMcpToolId(),
                node.getPromptSnippet(),
                node.getPromptTemplate(),
                node.getParamPromptTemplate(),
                node.getTopK(),
                node.getMinScore(),
                node.getSortOrder(),
                node.getEnabled(),
                node.getCreatedAt(),
                node.getUpdatedAt(),
                children
        );
    }

    private String writeExamples(List<String> examples) {
        List<String> cleaned = examples == null ? List.of() : examples.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "示例问题格式错误");
        }
    }

    private BigDecimal scoreOrNull(Double score) {
        if (score == null) {
            return null;
        }
        if (score < 0D || score > 1D) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "最低分数必须在 0 到 1 之间");
        }
        return BigDecimal.valueOf(score);
    }

    private Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim();
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
            // 事务提交后清理意图树缓存。
            public void afterCommit() {
                action.run();
            }
        });
    }
}
