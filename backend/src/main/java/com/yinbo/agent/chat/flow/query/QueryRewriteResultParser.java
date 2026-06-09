package com.yinbo.agent.chat.flow.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyMatch;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
// LLM 查询改写 JSON 响应容错解析器。
public class QueryRewriteResultParser {

    private final ObjectMapper objectMapper;

    // 注入 JSON 工具。
    public QueryRewriteResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 解析模型输出，解析失败时抛出异常交给上层降级。
    public QueryRewriteResult parse(
            String rawContent,
            String normalizedQuery,
            List<TerminologyMatch> matchedTerms
    ) {
        try {
            String json = extractJson(rawContent);
            RawRewriteResult rawResult = objectMapper.readValue(json, RawRewriteResult.class);
            String rewrite = rawResult.rewrite() == null || rawResult.rewrite().isBlank()
                    ? normalizedQuery
                    : rawResult.rewrite().trim();
            boolean shouldSplit = rawResult.shouldSplit() != null && rawResult.shouldSplit();
            List<String> subQuestions = rawResult.subQuestions();
            return QueryRewriteResult.llm(normalizedQuery, rewrite, shouldSplit, subQuestions, matchedTerms);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("LLM 查询改写 JSON 解析失败", exception);
        }
    }

    private String extractJson(String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json|JSON)?\\s*", "");
            content = content.replaceFirst("\\s*```$", "");
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("LLM 未返回 JSON 对象");
        }
        return content.substring(start, end + 1);
    }

    private record RawRewriteResult(
            String rewrite,
            @JsonProperty("should_split") Boolean shouldSplit,
            @JsonProperty("sub_questions") List<String> subQuestions
    ) {
    }
}
