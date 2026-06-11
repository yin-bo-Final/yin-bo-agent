package com.yinbo.agent.admin.dto;

import java.util.List;

// 管理后台通用分页响应。
public record PageResponse<T>(
        long page,
        long pageSize,
        long total,
        long pages,
        List<T> records
) {

    public PageResponse {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
