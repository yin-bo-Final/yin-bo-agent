package com.yinbo.mcp.logistics;

import java.util.List;

// 统一物流查询结果，供 MCP 工具格式化给用户。
public record LogisticsQueryResult(
        String trackingNo,
        String carrierCode,
        String carrierName,
        String state,
        String stateName,
        boolean signed,
        String currentLocation,
        String latestTime,
        String estimatedDelivery,
        List<LogisticsEvent> events
) {

    public LogisticsQueryResult {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
