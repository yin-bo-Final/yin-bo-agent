package com.yinbo.agent.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 120, message = "知识库名称不能超过120个字符")
        String name,
        @NotBlank(message = "Embedding 模型不能为空")
        @Size(max = 128, message = "Embedding 模型不能超过128个字符")
        String embeddingModel,
        @NotBlank(message = "collection 名称不能为空")
        @Size(max = 128, message = "collection 名称不能超过128个字符")
        String collectionName
) {
}
