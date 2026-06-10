package com.yinbo.agent.chat.flow.intent.model;

import java.time.LocalDateTime;
import java.util.List;

// 运行时意图规则。
public record IntentRule(
        String id,
        String ruleCode,
        String name,
        String description,
        String targetNodeCode,
        IntentRuleType ruleType,
        List<String> includeKeywords,
        IntentRuleMatchMode includeMatchMode,
        List<String> requireKeywords,
        IntentRuleMatchMode requireMatchMode,
        List<String> excludeKeywords,
        double score,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public IntentRule {
        includeKeywords = includeKeywords == null ? List.of() : List.copyOf(includeKeywords);
        requireKeywords = requireKeywords == null ? List.of() : List.copyOf(requireKeywords);
        excludeKeywords = excludeKeywords == null ? List.of() : List.copyOf(excludeKeywords);
        ruleType = ruleType == null ? IntentRuleType.STRONG : ruleType;
        includeMatchMode = includeMatchMode == null ? IntentRuleMatchMode.ANY : includeMatchMode;
        requireMatchMode = requireMatchMode == null ? IntentRuleMatchMode.ANY : requireMatchMode;
    }
}
