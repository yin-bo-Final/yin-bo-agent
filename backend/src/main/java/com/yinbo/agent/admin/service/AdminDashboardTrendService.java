package com.yinbo.agent.admin.service;

import com.yinbo.agent.admin.dto.AdminDashboardResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

// 管理后台趋势折线图聚合服务。
@Service
public class AdminDashboardTrendService {

    private static final String DAY_RANGE = "day";
    private static final String MONTH_RANGE = "month";
    private static final String THEME_COLOR = "#4C4F69";
    private static final String RESPONSE_TIME_COLOR = "#E09A1A";

    private final JdbcTemplate jdbcTemplate;

    // 注入趋势聚合使用的 JDBC 查询组件。
    public AdminDashboardTrendService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 将前端传入的趋势范围归一为支持的范围。
    public String normalizeRange(String trendRange) {
        return MONTH_RANGE.equalsIgnoreCase(trendRange) ? MONTH_RANGE : DAY_RANGE;
    }

    // 查询当前范围内所有 dashboard 趋势折线图数据。
    public List<AdminDashboardResponse.DashboardTrendSeries> queryDashboardTrendSeries(String trendRange) {
        String normalizedRange = normalizeRange(trendRange);
        TimeRange timeRange = resolveTimeRange(normalizedRange);
        return List.of(
                queryCountTrend(
                        "message",
                        "消息趋势",
                        "消息数",
                        "条",
                        THEME_COLOR,
                        "chat_message",
                        "created_at",
                        timeRange
                ),
                queryCountTrend(
                        "conversation",
                        "会话趋势",
                        "会话数",
                        "个",
                        THEME_COLOR,
                        "chat_conversation",
                        "created_at",
                        timeRange
                ),
                queryResponseTimeTrend(timeRange),
                queryDistinctUserTrend(
                        "activeUser",
                        "活跃用户趋势",
                        "活跃用户数",
                        "人",
                        THEME_COLOR,
                        "chat_message",
                        "created_at",
                        "user_id",
                        timeRange,
                        "role = 'user'"
                )
        );
    }

    // 查询兼容旧前端字段的消息趋势点。
    public List<AdminDashboardResponse.MessageTrendPoint> queryMessageTrendPoints(String trendRange) {
        AdminDashboardResponse.DashboardTrendSeries messageTrend = queryDashboardTrendSeries(trendRange).get(0);
        return messageTrend.points().stream()
                .map((point) -> new AdminDashboardResponse.MessageTrendPoint(point.label(), point.value()))
                .toList();
    }

    // 查询计数类趋势数据。
    private AdminDashboardResponse.DashboardTrendSeries queryCountTrend(
            String type,
            String title,
            String summaryLabel,
            String unit,
            String color,
            String tableName,
            String timeColumn,
            TimeRange timeRange
    ) {
        return queryCountTrend(type, title, summaryLabel, unit, color, tableName, timeColumn, timeRange, null);
    }

    // 查询带额外条件的计数类趋势数据。
    private AdminDashboardResponse.DashboardTrendSeries queryCountTrend(
            String type,
            String title,
            String summaryLabel,
            String unit,
            String color,
            String tableName,
            String timeColumn,
            TimeRange timeRange,
            String extraCondition
    ) {
        String whereCondition = extraCondition == null ? "" : " AND " + extraCondition;
        String sql = """
                SELECT %s AS bucket_index,
                       COUNT(*) AS trend_value
                FROM %s
                WHERE %s >= ? AND %s < ?%s
                GROUP BY bucket_index
                """.formatted(timeRange.extractExpression(timeColumn), tableName, timeColumn, timeColumn, whereCondition);
        Map<Integer, Long> valueByBucket = queryBucketValues(sql, timeRange);
        List<AdminDashboardResponse.DashboardTrendPoint> points = fillTrendPoints(timeRange, valueByBucket);
        long summaryValue = points.stream().mapToLong(AdminDashboardResponse.DashboardTrendPoint::value).sum();
        return new AdminDashboardResponse.DashboardTrendSeries(
                type,
                title,
                summaryLabel,
                unit,
                color,
                summaryValue,
                List.of(),
                points
        );
    }

    // 查询按用户去重的趋势数据。
    private AdminDashboardResponse.DashboardTrendSeries queryDistinctUserTrend(
            String type,
            String title,
            String summaryLabel,
            String unit,
            String color,
            String tableName,
            String timeColumn,
            String userColumn,
            TimeRange timeRange,
            String extraCondition
    ) {
        String whereCondition = extraCondition == null ? "" : " AND " + extraCondition;
        String sql = """
                SELECT %s AS bucket_index,
                       COUNT(DISTINCT %s) AS trend_value
                FROM %s
                WHERE %s >= ? AND %s < ?%s
                GROUP BY bucket_index
                """.formatted(
                timeRange.extractExpression(timeColumn),
                userColumn,
                tableName,
                timeColumn,
                timeColumn,
                whereCondition
        );
        Map<Integer, Long> valueByBucket = queryBucketValues(sql, timeRange);
        List<AdminDashboardResponse.DashboardTrendPoint> points = fillTrendPoints(timeRange, valueByBucket);
        Long summaryValue = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT %s)
                FROM %s
                WHERE %s >= ? AND %s < ?%s
                """.formatted(userColumn, tableName, timeColumn, timeColumn, whereCondition),
                Long.class,
                timeRange.startTime(),
                timeRange.endTime()
        );
        return new AdminDashboardResponse.DashboardTrendSeries(
                type,
                title,
                summaryLabel,
                unit,
                color,
                nullToZero(summaryValue),
                List.of(),
                points
        );
    }

    // 查询平均响应时间趋势数据。
    private AdminDashboardResponse.DashboardTrendSeries queryResponseTimeTrend(TimeRange timeRange) {
        String sql = """
                SELECT %s AS bucket_index,
                       ROUND(AVG(response_duration_ms))::BIGINT AS trend_value
                FROM chat_message
                WHERE role = 'assistant'
                  AND response_duration_ms IS NOT NULL
                  AND created_at >= ? AND created_at < ?
                GROUP BY bucket_index
                """.formatted(timeRange.extractExpression("created_at"));
        Map<Integer, Long> valueByBucket = queryBucketValues(sql, timeRange);
        List<AdminDashboardResponse.DashboardTrendPoint> points = fillTrendPoints(timeRange, valueByBucket);
        Long summaryValue = jdbcTemplate.queryForObject("""
                SELECT ROUND(AVG(response_duration_ms))::BIGINT
                FROM chat_message
                WHERE role = 'assistant'
                  AND response_duration_ms IS NOT NULL
                  AND created_at >= ? AND created_at < ?
                """, Long.class, timeRange.startTime(), timeRange.endTime());
        return new AdminDashboardResponse.DashboardTrendSeries(
                "responseTime",
                "响应时间趋势",
                "平均响应时间",
                "毫秒",
                RESPONSE_TIME_COLOR,
                nullToZero(summaryValue),
                List.of(
                        new AdminDashboardResponse.DashboardTrendThreshold(10000, "良好 ≤10s", "#4AA3DF"),
                        new AdminDashboardResponse.DashboardTrendThreshold(15000, "警告 >15s", "#FF6B6B")
                ),
                points
        );
    }

    // 查询指定 SQL 的分桶数值。
    private Map<Integer, Long> queryBucketValues(String sql, TimeRange timeRange) {
        Map<Integer, Long> valueByBucket = new HashMap<>();
        RowCallbackHandler rowHandler = (rs) -> valueByBucket.put(
                rs.getInt("bucket_index"),
                rs.getLong("trend_value")
        );
        jdbcTemplate.query(sql, rowHandler, timeRange.startTime(), timeRange.endTime());
        return valueByBucket;
    }

    // 按当前范围补齐缺失时间点为 0。
    private List<AdminDashboardResponse.DashboardTrendPoint> fillTrendPoints(
            TimeRange timeRange,
            Map<Integer, Long> valueByBucket
    ) {
        List<AdminDashboardResponse.DashboardTrendPoint> points = new ArrayList<>();
        for (int bucket = timeRange.startBucket(); bucket <= timeRange.endBucket(); bucket++) {
            points.add(new AdminDashboardResponse.DashboardTrendPoint(
                    timeRange.label(bucket),
                    valueByBucket.getOrDefault(bucket, 0L)
            ));
        }
        return points;
    }

    // 解析 dashboard 趋势范围对应的时间边界。
    private TimeRange resolveTimeRange(String trendRange) {
        if (MONTH_RANGE.equals(trendRange)) {
            LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
            return new TimeRange(
                    MONTH_RANGE,
                    monthStart.atStartOfDay(),
                    monthStart.plusMonths(1).atStartOfDay(),
                    1,
                    monthStart.lengthOfMonth()
            );
        }
        LocalDate today = LocalDate.now();
        return new TimeRange(
                DAY_RANGE,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                0,
                23
        );
    }

    // 将数据库聚合查询得到的空值转换为 0。
    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    // 趋势查询使用的时间范围和横轴标签规则。
    private record TimeRange(
            String range,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int startBucket,
            int endBucket
    ) {
        // 返回当前范围在 SQL 中使用的分桶表达式。
        private String extractExpression(String timeColumn) {
            return MONTH_RANGE.equals(range)
                    ? "EXTRACT(DAY FROM " + timeColumn + ")::INT"
                    : "EXTRACT(HOUR FROM " + timeColumn + ")::INT";
        }

        // 返回当前分桶的前端横轴标签。
        private String label(int bucket) {
            return MONTH_RANGE.equals(range) ? bucket + "日" : bucket + "点";
        }
    }
}
