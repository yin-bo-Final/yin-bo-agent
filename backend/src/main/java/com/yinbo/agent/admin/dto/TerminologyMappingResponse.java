package com.yinbo.agent.admin.dto;

import java.time.LocalDateTime;

// 管理后台关键词映射响应。
public record TerminologyMappingResponse(
        String termId,
        String aliasId,
        String aliasName,
        String canonicalName,
        String termType,
        String description,
        Integer priority,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
