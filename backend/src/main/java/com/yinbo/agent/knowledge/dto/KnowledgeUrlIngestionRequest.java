package com.yinbo.agent.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 知识库 URL 文档入库请求。
public record KnowledgeUrlIngestionRequest(
        @NotBlank(message = "URL 不能为空")
        @Size(max = 2048, message = "URL 长度不能超过2048个字符")
        String url,
        @Size(max = 255, message = "文件名长度不能超过255个字符")
        String fileName,
        String strategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunks
) {
}
