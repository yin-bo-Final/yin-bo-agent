package com.yinbo.agent.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChunkRequest(
        @NotBlank(message = "分块内容不能为空")
        @Size(max = 20000, message = "分块内容不能超过 20000 个字符")
        String content
) {
}
