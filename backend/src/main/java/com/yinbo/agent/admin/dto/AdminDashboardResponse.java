package com.yinbo.agent.admin.dto;

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
