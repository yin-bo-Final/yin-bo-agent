package com.yinbo.agent.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

// 管理后台意图树节点响应。
public record IntentNodeResponse(
        String id,
        String nodeCode,
        String parentCode,
        String name,
        String description,
        String level,
        String kind,
        List<String> examples,
        String fullPath,
        Boolean leaf,
        String knowledgeBaseNo,
        String collectionName,
        String mcpToolId,
        String promptSnippet,
        String promptTemplate,
        String paramPromptTemplate,
        Integer topK,
        Double minScore,
        Integer sortOrder,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<IntentNodeResponse> children
) {

    public IntentNodeResponse {
        examples = examples == null ? List.of() : List.copyOf(examples);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
