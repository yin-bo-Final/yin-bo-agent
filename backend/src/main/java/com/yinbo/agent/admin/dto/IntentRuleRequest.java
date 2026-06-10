package com.yinbo.agent.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

// 新增或修改意图规则请求。
public record IntentRuleRequest(
        @NotBlank(message = "规则编码不能为空")
        @Size(max = 128, message = "规则编码不能超过 128 个字符")
        String ruleCode,

        @NotBlank(message = "规则名称不能为空")
        @Size(max = 128, message = "规则名称不能超过 128 个字符")
        String name,

        @Size(max = 512, message = "规则描述不能超过 512 个字符")
        String description,

        @NotBlank(message = "目标节点不能为空")
        @Size(max = 128, message = "目标节点不能超过 128 个字符")
        String targetNodeCode,

        @NotBlank(message = "规则类型不能为空")
        @Size(max = 32, message = "规则类型不能超过 32 个字符")
        String ruleType,

        List<String> includeKeywords,

        @Size(max = 16, message = "包含词匹配模式不能超过 16 个字符")
        String includeMatchMode,

        List<String> requireKeywords,

        @Size(max = 16, message = "必要词匹配模式不能超过 16 个字符")
        String requireMatchMode,

        List<String> excludeKeywords,

        Double score,

        Boolean enabled
) {
}
