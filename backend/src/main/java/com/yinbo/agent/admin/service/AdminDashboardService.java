package com.yinbo.agent.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.admin.dto.AdminDashboardResponse;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.auth.mapper.AuthUserMapper;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.mapper.ChatConversationMapper;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

@Service
// 管理后台仪表盘统计服务。
public class AdminDashboardService {

    private final AuthUserMapper authUserMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatConversationMapper chatConversationMapper;
    private final JdbcTemplate jdbcTemplate;

    // 注入仪表盘统计需要的用户、消息、会话和 JDBC 查询组件。
    public AdminDashboardService(
            AuthUserMapper authUserMapper,
            ChatMessageMapper chatMessageMapper,
            ChatConversationMapper chatConversationMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.authUserMapper = authUserMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatConversationMapper = chatConversationMapper;
        this.jdbcTemplate = jdbcTemplate;
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

        return new AdminDashboardResponse(
                activeUserCount,
                messageCount,
                conversationCount,
                trafficCharacterCount,
                averageResponseTimeMs,
                null,
                null,
                normalizeMessageRange(messageRange),
                queryMessageTrendPoints(normalizeMessageRange(messageRange))
        );
    }

    private String normalizeMessageRange(String messageRange) {
        return "month".equalsIgnoreCase(messageRange) ? "month" : "day";
    }

    private List<AdminDashboardResponse.MessageTrendPoint> queryMessageTrendPoints(String messageRange) {
        return "month".equals(messageRange) ? queryMonthlyMessageTrendPoints() : queryDailyMessageTrendPoints();
    }

    // 今日消息曲线，按 0-23 点补齐。
    private List<AdminDashboardResponse.MessageTrendPoint> queryDailyMessageTrendPoints() {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        String sql = """
                SELECT EXTRACT(HOUR FROM created_at)::INT AS bucket_hour,
                       COUNT(*) AS message_count
                FROM chat_message
                WHERE created_at >= ? AND created_at < ?
                GROUP BY bucket_hour
                """;
        Map<Integer, Long> countByHour = new HashMap<>();
        RowCallbackHandler rowHandler = (rs) -> countByHour.put(rs.getInt("bucket_hour"), rs.getLong("message_count"));
        jdbcTemplate.query(sql, rowHandler, startTime, endTime);

        List<AdminDashboardResponse.MessageTrendPoint> points = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            points.add(new AdminDashboardResponse.MessageTrendPoint(hour + "点", countByHour.getOrDefault(hour, 0L)));
        }
        return points;
    }

    // 本月消息曲线，按自然日补齐。
    private List<AdminDashboardResponse.MessageTrendPoint> queryMonthlyMessageTrendPoints() {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        String sql = """
                SELECT EXTRACT(DAY FROM created_at)::INT AS bucket_day,
                       COUNT(*) AS message_count
                FROM chat_message
                WHERE created_at >= ? AND created_at < ?
                GROUP BY bucket_day
                """;
        Map<Integer, Long> countByDay = new HashMap<>();
        RowCallbackHandler rowHandler = (rs) -> countByDay.put(rs.getInt("bucket_day"), rs.getLong("message_count"));
        jdbcTemplate.query(sql, rowHandler, monthStart.atStartOfDay(), nextMonthStart.atStartOfDay());

        List<AdminDashboardResponse.MessageTrendPoint> points = new ArrayList<>();
        for (int day = 1; day <= monthStart.lengthOfMonth(); day++) {
            points.add(new AdminDashboardResponse.MessageTrendPoint(day + "日", countByDay.getOrDefault(day, 0L)));
        }
        return points;
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
}
