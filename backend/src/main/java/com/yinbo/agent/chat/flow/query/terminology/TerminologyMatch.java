package com.yinbo.agent.chat.flow.query.terminology;

// 用户问题中命中的术语别名。
public record TerminologyMatch(
        Long termId,
        Long aliasId,
        String raw,
        String canonical,
        String termType,
        int start,
        int end
) {
}
