package com.yinbo.agent.admin.dto;

// 管理后台仪表盘统计响应。
public record AdminDashboardResponse(
        long activeUserCount,
        long messageCount,
        long conversationCount,
        long trafficCharacterCount,
        Long averageResponseTimeMs,
        Double knowledgeErrorRate,
        Double noKnowledgeRate
) {
}
