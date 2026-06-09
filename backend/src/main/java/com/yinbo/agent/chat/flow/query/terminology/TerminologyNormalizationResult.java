package com.yinbo.agent.chat.flow.query.terminology;

import java.util.List;

// 术语统一后的查询和命中术语列表。
public record TerminologyNormalizationResult(
        String originalQuery,
        String normalizedQuery,
        List<TerminologyMatch> matches
) {

    // 规范化术语统一结果。
    public TerminologyNormalizationResult {
        originalQuery = originalQuery == null ? "" : originalQuery;
        normalizedQuery = normalizedQuery == null || normalizedQuery.isBlank() ? originalQuery : normalizedQuery;
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
