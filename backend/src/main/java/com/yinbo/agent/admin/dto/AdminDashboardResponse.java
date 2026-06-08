package com.yinbo.agent.admin.dto;

import java.util.List;

// 管理后台仪表盘统计响应。
public record AdminDashboardResponse(
        long activeUserCount,
        long messageCount,
        long conversationCount,
        long trafficCharacterCount,
        Long averageResponseTimeMs,
        Double knowledgeErrorRate,
        Double noKnowledgeRate,
        String messageTrendRange,
        List<MessageTrendPoint> messageTrendPoints
) {
    public record MessageTrendPoint(
            String label,
            long messageCount
    ) {
    }
}
