package com.yinbo.agent.chat.flow.intent.model;

import java.util.List;
import java.util.Map;

// 意图树运行时快照。
public record IntentTreeData(
        List<IntentNode> roots,
        List<IntentNode> allNodes,
        List<IntentNode> leafNodes,
        Map<String, IntentNode> nodeById
) {

    public IntentTreeData {
        roots = roots == null ? List.of() : List.copyOf(roots);
        allNodes = allNodes == null ? List.of() : List.copyOf(allNodes);
        leafNodes = leafNodes == null ? List.of() : List.copyOf(leafNodes);
        nodeById = nodeById == null ? Map.of() : Map.copyOf(nodeById);
    }
}
