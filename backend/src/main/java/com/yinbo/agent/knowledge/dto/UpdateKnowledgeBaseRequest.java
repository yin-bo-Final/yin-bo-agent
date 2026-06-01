package com.yinbo.agent.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 更新知识库请求。
public record UpdateKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 120, message = "知识库名称不能超过120个字符")
        String name
) {
}
