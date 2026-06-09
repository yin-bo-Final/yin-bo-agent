package com.yinbo.agent.chat.flow.query;

import com.yinbo.agent.chat.flow.query.terminology.TerminologyMatch;
import java.util.List;

// 查询改写阶段的结构化输出。
public record QueryRewriteResult(
        String rewrite,
        boolean shouldSplit,
        List<String> subQuestions,
        String normalizedQuery,
        List<TerminologyMatch> matchedTerms,
        String sourceType,
        String fallbackReason,
        boolean success
) {

    // 规范化子问题和术语列表。
    public QueryRewriteResult {
        rewrite = normalizeText(rewrite);
        normalizedQuery = normalizeText(normalizedQuery);
        if (rewrite.isBlank()) {
            rewrite = normalizedQuery;
        }
        subQuestions = normalizeSubQuestions(subQuestions, rewrite, shouldSplit);
        shouldSplit = subQuestions.size() > 1;
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
        sourceType = sourceType == null || sourceType.isBlank() ? "FALLBACK" : sourceType.trim();
        fallbackReason = fallbackReason == null || fallbackReason.isBlank() ? null : fallbackReason.trim();
    }

    // 构造仅使用归一化问题的兜底结果。
    public static QueryRewriteResult fallback(
            String normalizedQuery,
            List<TerminologyMatch> matchedTerms,
            String fallbackReason
    ) {
        String query = normalizeText(normalizedQuery);
        return new QueryRewriteResult(
                query,
                false,
                List.of(query),
                query,
                matchedTerms,
                "FALLBACK",
                fallbackReason,
                false
        );
    }

    // 构造规则拆分结果。
    public static QueryRewriteResult ruleSplit(
            String normalizedQuery,
            List<String> subQuestions,
            List<TerminologyMatch> matchedTerms,
            String fallbackReason
    ) {
        String query = normalizeText(normalizedQuery);
        return new QueryRewriteResult(
                query,
                subQuestions != null && subQuestions.size() > 1,
                subQuestions,
                query,
                matchedTerms,
                "RULE_SPLIT",
                fallbackReason,
                true
        );
    }

    // 构造 LLM 改写结果。
    public static QueryRewriteResult llm(
            String normalizedQuery,
            String rewrite,
            boolean shouldSplit,
            List<String> subQuestions,
            List<TerminologyMatch> matchedTerms
    ) {
        return new QueryRewriteResult(
                rewrite,
                shouldSplit,
                subQuestions,
                normalizedQuery,
                matchedTerms,
                "LLM",
                null,
                true
        );
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeSubQuestions(List<String> subQuestions, String rewrite, boolean shouldSplit) {
        List<String> normalized = subQuestions == null
                ? List.of()
                : subQuestions.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (!shouldSplit || normalized.size() < 2) {
            return List.of(rewrite);
        }
        return List.copyOf(normalized);
    }
}
