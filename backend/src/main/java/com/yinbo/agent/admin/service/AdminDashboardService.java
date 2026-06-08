package com.yinbo.agent.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.admin.dto.AdminDashboardResponse;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.auth.mapper.AuthUserMapper;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.mapper.ChatConversationMapper;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// 管理后台仪表盘统计服务。
@Service
public class AdminDashboardService {

    private final AuthUserMapper authUserMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatConversationMapper chatConversationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AdminDashboardTrendService dashboardTrendService;

    // 注入仪表盘统计需要的用户、消息、会话和 JDBC 查询组件。
    public AdminDashboardService(
            AuthUserMapper authUserMapper,
            ChatMessageMapper chatMessageMapper,
            ChatConversationMapper chatConversationMapper,
            JdbcTemplate jdbcTemplate,
            AdminDashboardTrendService dashboardTrendService
    ) {
        this.authUserMapper = authUserMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatConversationMapper = chatConversationMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.dashboardTrendService = dashboardTrendService;
    }

    // 汇总管理后台首页需要的核心统计指标。
    public AdminDashboardResponse dashboard(String messageRange) {
        long activeUserCount = nullToZero(authUserMapper.selectCount(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getStatus, 1)
                .ge(AuthUser::getLastLoginAt, LocalDateTime.now().minusDays(1))));
        long messageCount = nullToZero(chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessageEntity>()));
        long conversationCount = nullToZero(chatConversationMapper.selectCount(new LambdaQueryWrapper<ChatConversation>()));
        long trafficCharacterCount = queryLongOrZero("SELECT COALESCE(SUM(length(content)), 0) FROM chat_message");
        Long averageResponseTimeMs = queryNullableLong(
                "SELECT ROUND(AVG(response_duration_ms))::BIGINT FROM chat_message WHERE role = 'assistant' AND response_duration_ms IS NOT NULL"
        );
        String normalizedTrendRange = dashboardTrendService.normalizeRange(messageRange);
        List<AdminDashboardResponse.DashboardTrendSeries> dashboardTrendSeries =
                dashboardTrendService.queryDashboardTrendSeries(normalizedTrendRange);

        return new AdminDashboardResponse(
                activeUserCount,
                messageCount,
                conversationCount,
                trafficCharacterCount,
                averageResponseTimeMs,
                null,
                null,
                normalizedTrendRange,
                extractMessageTrendPoints(dashboardTrendSeries),
                dashboardTrendSeries
        );
    }

    // 将数据库聚合查询得到的空值转换为 0。
    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    // 查询必须返回数值的 JDBC 统计指标，空值统一按 0 处理。
    private long queryLongOrZero(String sql) {
        return nullToZero(jdbcTemplate.queryForObject(sql, Long.class));
    }

    // 查询允许暂无数据的 JDBC 统计指标，保留 null 表示暂无结果。
    private Long queryNullableLong(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    // 从通用趋势列表中提取兼容旧字段的消息趋势。
    private List<AdminDashboardResponse.MessageTrendPoint> extractMessageTrendPoints(
            List<AdminDashboardResponse.DashboardTrendSeries> dashboardTrendSeries
    ) {
        return dashboardTrendSeries.stream()
                .filter((series) -> "message".equals(series.type()))
                .findFirst()
                .map((series) -> series.points().stream()
                        .map((point) -> new AdminDashboardResponse.MessageTrendPoint(point.label(), point.value()))
                        .toList())
                .orElse(List.of());
    }
}
