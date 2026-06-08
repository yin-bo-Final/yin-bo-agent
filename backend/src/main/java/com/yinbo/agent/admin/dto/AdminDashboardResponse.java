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
        List<MessageTrendPoint> messageTrendPoints,
        List<DashboardTrendSeries> dashboardTrendSeries
) {
    // 兼容旧前端消息曲线字段的数据点。
    public record MessageTrendPoint(
            String label,
            long messageCount
    ) {
    }

    // 后台 Dashboard 通用趋势折线图数据。
    public record DashboardTrendSeries(
            String type,
            String title,
            String summaryLabel,
            String unit,
            String color,
            long summaryValue,
            List<DashboardTrendThreshold> thresholds,
            List<DashboardTrendPoint> points
    ) {
    }

    // 后台 Dashboard 通用趋势折线图数据点。
    public record DashboardTrendPoint(
            String label,
            long value
    ) {
    }

    // 后台 Dashboard 趋势折线图阈值参考线。
    public record DashboardTrendThreshold(
            long value,
            String label,
            String color
    ) {
    }
}
