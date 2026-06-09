package com.yinbo.agent.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 新增或修改关键词映射请求。
public record TerminologyMappingRequest(
        @NotBlank(message = "原始词不能为空")
        @Size(max = 128, message = "原始词不能超过 128 个字符")
        String aliasName,

        @NotBlank(message = "目标词不能为空")
        @Size(max = 128, message = "目标词不能超过 128 个字符")
        String canonicalName,

        @Size(max = 64, message = "术语类型不能超过 64 个字符")
        String termType,

        @Size(max = 512, message = "备注不能超过 512 个字符")
        String description,

        Integer priority,

        Boolean enabled
) {
}
