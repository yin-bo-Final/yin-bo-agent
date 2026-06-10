package com.yinbo.agent.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

// 新增或修改意图树节点请求。
public record IntentNodeRequest(
        @NotBlank(message = "节点编码不能为空")
        @Size(max = 128, message = "节点编码不能超过 128 个字符")
        String nodeCode,

        @Size(max = 128, message = "父节点编码不能超过 128 个字符")
        String parentCode,

        @NotBlank(message = "节点名称不能为空")
        @Size(max = 128, message = "节点名称不能超过 128 个字符")
        String name,

        @Size(max = 512, message = "节点描述不能超过 512 个字符")
        String description,

        @NotBlank(message = "节点层级不能为空")
        @Size(max = 32, message = "节点层级不能超过 32 个字符")
        String level,

        @NotBlank(message = "意图类型不能为空")
        @Size(max = 32, message = "意图类型不能超过 32 个字符")
        String kind,

        List<String> examples,

        @Size(max = 64, message = "知识库编号不能超过 64 个字符")
        String knowledgeBaseNo,

        @Size(max = 128, message = "Collection 不能超过 128 个字符")
        String collectionName,

        @Size(max = 128, message = "MCP 工具 ID 不能超过 128 个字符")
        String mcpToolId,

        String promptSnippet,

        String promptTemplate,

        String paramPromptTemplate,

        Integer topK,

        Double minScore,

        Integer sortOrder,

        Boolean enabled
) {
}
