package com.yinbo.agent.admin.dto;

import java.util.List;

// 管理后台意图识别记录分页响应。
public record IntentResolveRecordPageResponse(
        long page,
        long pageSize,
        long total,
        long pages,
        List<IntentResolveRecordResponse> records
) {

    public IntentResolveRecordPageResponse {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
