package com.yinbo.agent.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

// 管理后台意图规则响应。
public record IntentRuleResponse(
        String id,
        String ruleCode,
        String name,
        String description,
        String targetNodeCode,
        String targetNodeName,
        String targetNodePath,
        String ruleType,
        List<String> includeKeywords,
        String includeMatchMode,
        List<String> requireKeywords,
        String requireMatchMode,
        List<String> excludeKeywords,
        Double score,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public IntentRuleResponse {
        includeKeywords = includeKeywords == null ? List.of() : List.copyOf(includeKeywords);
        requireKeywords = requireKeywords == null ? List.of() : List.copyOf(requireKeywords);
        excludeKeywords = excludeKeywords == null ? List.of() : List.copyOf(excludeKeywords);
    }
}
