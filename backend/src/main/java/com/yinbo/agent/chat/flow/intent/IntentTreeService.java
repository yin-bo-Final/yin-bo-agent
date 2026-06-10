package com.yinbo.agent.chat.flow.intent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.entity.ChatIntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentKind;
import com.yinbo.agent.chat.flow.intent.model.IntentLevel;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentTreeData;
import com.yinbo.agent.chat.mapper.ChatIntentNodeMapper;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
// 意图树加载、组装和缓存服务。
public class IntentTreeService {

    private static final Logger log = LoggerFactory.getLogger(IntentTreeService.class);
    private static final TypeReference<List<String>> EXAMPLES_TYPE = new TypeReference<>() {
    };

    private final ChatIntentNodeMapper intentNodeMapper;
    private final ObjectMapper objectMapper;
    private final IntentTreeCacheService cacheService;

    // 注入意图节点 Mapper、JSON 工具和缓存服务。
    public IntentTreeService(
            ChatIntentNodeMapper intentNodeMapper,
            ObjectMapper objectMapper,
            IntentTreeCacheService cacheService
    ) {
        this.intentNodeMapper = intentNodeMapper;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    // 加载启用状态的意图树运行时快照。
    public IntentTreeData loadEnabledTreeData() {
        List<IntentNode> roots = cacheService.get();
        if (roots == null) {
            roots = loadTreeFromDatabase(true);
            cacheService.put(roots);
        }
        return toTreeData(roots);
    }

    // 后台读取完整意图树，包含禁用节点。
    public IntentTreeData loadAdminTreeData() {
        return toTreeData(loadTreeFromDatabase(false));
    }

    // 清理意图树缓存。
    public void evictCache() {
        cacheService.evict();
    }

    // 将数据库扁平行组装为树。
    private List<IntentNode> loadTreeFromDatabase(boolean enabledOnly) {
        LambdaQueryWrapper<ChatIntentNode> query = new LambdaQueryWrapper<ChatIntentNode>()
                .orderByAsc(ChatIntentNode::getSortOrder)
                .orderByAsc(ChatIntentNode::getId);
        if (enabledOnly) {
            query.eq(ChatIntentNode::getEnabled, true);
        }
        List<ChatIntentNode> rows = intentNodeMapper.selectList(query);
        Map<String, IntentNode> nodeMap = new LinkedHashMap<>();
        for (ChatIntentNode row : rows) {
            IntentNode node = toRuntimeNode(row);
            if (node.getId() != null && !node.getId().isBlank()) {
                nodeMap.put(node.getId(), node);
            }
        }
        List<IntentNode> roots = new ArrayList<>();
        for (IntentNode node : nodeMap.values()) {
            if (node.getParentId() == null || node.getParentId().isBlank()) {
                roots.add(node);
                continue;
            }
            IntentNode parent = nodeMap.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        sortTree(roots);
        fillFullPath(roots, null);
        return roots;
    }

    // 构建运行时快照索引。
    public IntentTreeData toTreeData(List<IntentNode> roots) {
        List<IntentNode> safeRoots = roots == null ? List.of() : roots;
        List<IntentNode> allNodes = flatten(safeRoots);
        List<IntentNode> leafNodes = allNodes.stream()
                .filter(IntentNode::isLeaf)
                .toList();
        Map<String, IntentNode> nodeById = new HashMap<>();
        for (IntentNode node : allNodes) {
            if (node.getId() != null) {
                nodeById.put(node.getId(), node);
            }
        }
        return new IntentTreeData(safeRoots, allNodes, leafNodes, nodeById);
    }

    // 扁平化树节点。
    public List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> result = new ArrayList<>();
        ArrayDeque<IntentNode> stack = new ArrayDeque<>();
        List<IntentNode> safeRoots = roots == null ? List.of() : roots;
        for (int index = safeRoots.size() - 1; index >= 0; index--) {
            stack.push(safeRoots.get(index));
        }
        while (!stack.isEmpty()) {
            IntentNode current = stack.pop();
            result.add(current);
            List<IntentNode> children = current.getChildren();
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
            }
        }
        return result;
    }

    private IntentNode toRuntimeNode(ChatIntentNode row) {
        IntentNode node = new IntentNode();
        node.setDbId(row.getId() == null ? null : row.getId().toString());
        node.setId(row.getNodeCode());
        node.setParentId(blankToNull(row.getParentCode()));
        node.setName(row.getName());
        node.setDescription(row.getDescription());
        node.setLevel(IntentLevel.from(row.getLevel()));
        node.setKind(IntentKind.from(row.getKind()));
        node.setExamples(parseExamples(row.getExamplesJson()));
        node.setKnowledgeBaseNo(row.getKnowledgeBaseNo());
        node.setCollectionName(row.getCollectionName());
        node.setMcpToolId(row.getMcpToolId());
        node.setPromptSnippet(row.getPromptSnippet());
        node.setPromptTemplate(row.getPromptTemplate());
        node.setParamPromptTemplate(row.getParamPromptTemplate());
        node.setTopK(row.getTopK());
        node.setMinScore(toDouble(row.getMinScore()));
        node.setSortOrder(row.getSortOrder() == null ? 0 : row.getSortOrder());
        node.setEnabled(row.getEnabled() == null || row.getEnabled());
        node.setCreatedAt(row.getCreatedAt());
        node.setUpdatedAt(row.getUpdatedAt());
        return node;
    }

    private List<String> parseExamples(String examplesJson) {
        if (examplesJson == null || examplesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(examplesJson, EXAMPLES_TYPE).stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (JsonProcessingException exception) {
            log.warn("event=intent_examples_decode_failed value={}", sanitizeLogValue(examplesJson));
            return List.of();
        }
    }

    private void sortTree(List<IntentNode> nodes) {
        nodes.sort(Comparator
                .comparing((IntentNode node) -> node.getSortOrder() == null ? 0 : node.getSortOrder())
                .thenComparing(IntentNode::getId, Comparator.nullsLast(String::compareTo)));
        for (IntentNode node : nodes) {
            sortTree(node.getChildren());
        }
    }

    private void fillFullPath(List<IntentNode> nodes, String prefix) {
        for (IntentNode node : nodes) {
            String path = prefix == null || prefix.isBlank() ? node.getName() : prefix + " > " + node.getName();
            node.setFullPath(path);
            fillFullPath(node.getChildren(), path);
        }
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
