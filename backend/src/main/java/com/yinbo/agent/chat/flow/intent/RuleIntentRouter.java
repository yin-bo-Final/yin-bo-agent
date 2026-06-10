package com.yinbo.agent.chat.flow.intent;

import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentRule;
import com.yinbo.agent.chat.flow.intent.model.IntentRuleMatchMode;
import com.yinbo.agent.chat.flow.intent.model.IntentRuleType;
import com.yinbo.agent.chat.flow.intent.model.IntentTreeData;
import com.yinbo.agent.chat.flow.intent.model.NodeScore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
// 轻量规则路由：强规则直接命中叶子，弱规则只缩小候选范围。
public class RuleIntentRouter {

    private final IntentRuleService intentRuleService;

    // 注入可配置意图规则服务。
    public RuleIntentRouter(IntentRuleService intentRuleService) {
        this.intentRuleService = intentRuleService;
    }

    // 对单个问题执行规则路由。
    public RuleRouteResult route(String question, IntentTreeData treeData) {
        String normalized = normalize(question);
        if (normalized.isBlank() || treeData == null) {
            return RuleRouteResult.empty();
        }

        List<IntentRule> matchedRules = intentRuleService.enabledRules().stream()
                .filter(rule -> matches(normalized, rule))
                .toList();
        List<NodeScore> strongScores = strongLeafMatches(matchedRules, treeData);
        if (!strongScores.isEmpty()) {
            return new RuleRouteResult(strongScores, List.of(), true, "STRONG_RULE");
        }

        List<IntentNode> narrowedLeaves = weakCandidateLeaves(matchedRules, treeData);
        return new RuleRouteResult(List.of(), narrowedLeaves, !narrowedLeaves.isEmpty(), "WEAK_RULE");
    }

    private List<NodeScore> strongLeafMatches(List<IntentRule> matchedRules, IntentTreeData treeData) {
        List<NodeScore> scores = new ArrayList<>();
        for (IntentRule rule : matchedRules) {
            if (rule.ruleType() == IntentRuleType.STRONG) {
                addScore(scores, treeData, rule);
            }
        }
        return scores.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList();
    }

    private List<IntentNode> weakCandidateLeaves(List<IntentRule> matchedRules, IntentTreeData treeData) {
        Set<IntentNode> leaves = new LinkedHashSet<>();
        for (IntentRule rule : matchedRules) {
            if (rule.ruleType() != IntentRuleType.WEAK) {
                continue;
            }
            IntentNode target = treeData.nodeById().get(rule.targetNodeCode());
            if (target != null) {
                leaves.addAll(leafNodes(target));
            }
        }
        return new ArrayList<>(leaves);
    }

    private List<IntentNode> leafNodes(IntentNode root) {
        if (root.isLeaf()) {
            return List.of(root);
        }
        List<IntentNode> leaves = new ArrayList<>();
        for (IntentNode child : root.getChildren()) {
            leaves.addAll(leafNodes(child));
        }
        return leaves;
    }

    private void addScore(List<NodeScore> scores, IntentTreeData treeData, IntentRule rule) {
        IntentNode node = treeData.nodeById().get(rule.targetNodeCode());
        if (node != null && node.isLeaf()) {
            scores.add(new NodeScore(node, rule.score(), "命中规则：" + rule.name(), "RULE"));
        }
    }

    private boolean matches(String normalized, IntentRule rule) {
        if (!rule.enabled()) {
            return false;
        }
        if (rule.includeKeywords().isEmpty() && rule.requireKeywords().isEmpty()) {
            return false;
        }
        if (!rule.excludeKeywords().isEmpty()
                && matchKeywords(normalized, rule.excludeKeywords(), IntentRuleMatchMode.ANY)) {
            return false;
        }
        return matchKeywords(normalized, rule.includeKeywords(), rule.includeMatchMode())
                && matchKeywords(normalized, rule.requireKeywords(), rule.requireMatchMode());
    }

    private boolean matchKeywords(String text, List<String> keywords, IntentRuleMatchMode mode) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        if (mode == IntentRuleMatchMode.ALL) {
            return keywords.stream().allMatch(keyword -> containsKeyword(text, normalize(keyword)));
        }
        return keywords.stream().anyMatch(keyword -> containsKeyword(text, normalize(keyword)));
    }

    private boolean containsKeyword(String text, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        if (keyword.length() <= 2 && keyword.matches("[a-z0-9]+")) {
            return text.equals(keyword);
        }
        return text.contains(keyword);
    }

    private String normalize(String question) {
        if (question == null) {
            return "";
        }
        return question.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    // 规则路由结果。
    public record RuleRouteResult(
            List<NodeScore> strongScores,
            List<IntentNode> candidateLeaves,
            boolean matched,
            String source
    ) {

        public RuleRouteResult {
            strongScores = strongScores == null ? List.of() : List.copyOf(strongScores);
            candidateLeaves = candidateLeaves == null ? List.of() : List.copyOf(candidateLeaves);
        }

        public static RuleRouteResult empty() {
            return new RuleRouteResult(List.of(), List.of(), false, "NONE");
        }
    }
}
