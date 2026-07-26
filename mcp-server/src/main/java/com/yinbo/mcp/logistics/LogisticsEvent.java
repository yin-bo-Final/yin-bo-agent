package com.yinbo.mcp.logistics;

// 单条物流轨迹事件。
public record LogisticsEvent(
        String time,
        String context,
        String location,
        String status
) {
}
