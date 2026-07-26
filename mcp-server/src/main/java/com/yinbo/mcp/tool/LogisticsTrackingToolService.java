package com.yinbo.mcp.tool;

import com.yinbo.mcp.logistics.LogisticsEvent;
import com.yinbo.mcp.logistics.LogisticsProvider;
import com.yinbo.mcp.logistics.LogisticsProviderException;
import com.yinbo.mcp.logistics.LogisticsQueryRequest;
import com.yinbo.mcp.logistics.LogisticsQueryResult;
import com.yinbo.mcp.tool.dto.McpToolCallRequest;
import com.yinbo.mcp.tool.dto.McpToolCallResponse;
import com.yinbo.mcp.tool.dto.McpToolDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
// 物流轨迹查询 MCP 工具。
public class LogisticsTrackingToolService {

    public static final String TOOL_ID = "logistics-tracking-tool";
    private static final Pattern TRACKING_NO_PATTERN = Pattern.compile("(?i)(?:SF|YT|YD|ZTO|STO|EMS|JD)?[A-Z0-9]{8,24}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:1[3-9]\\d{9}|\\d{4})(?!\\d)");

    private final LogisticsProvider logisticsProvider;

    public LogisticsTrackingToolService(LogisticsProvider logisticsProvider) {
        this.logisticsProvider = logisticsProvider;
    }

    // 工具元信息。
    public McpToolDescriptor descriptor() {
        return new McpToolDescriptor(
                TOOL_ID,
                "物流轨迹查询",
                "根据快递单号查询包裹当前状态、最新位置和轨迹事件。",
                List.of("trackingNo")
        );
    }

    // 执行物流轨迹查询。
    public McpToolCallResponse call(McpToolCallRequest request) {
        long startedAt = System.nanoTime();
        String trackingNo = resolveTrackingNo(request);
        if (trackingNo == null) {
            return McpToolCallResponse.clarification(
                    TOOL_ID,
                    "请提供快递单号，我才能帮你查询当前物流轨迹。",
                    elapsedMillis(startedAt)
            );
        }

        try {
            LogisticsQueryResult result = logisticsProvider.query(new LogisticsQueryRequest(
                    trackingNo,
                    resolveCarrierCode(request, trackingNo),
                    resolveCarrierName(request),
                    resolvePhone(request),
                    resolveStringArgument(request, "from"),
                    resolveStringArgument(request, "to")
            ));
            return McpToolCallResponse.success(
                    TOOL_ID,
                    formatMessage(result),
                    toDataMap(result),
                    elapsedMillis(startedAt)
            );
        } catch (LogisticsProviderException exception) {
            if (exception.needClarification()) {
                return McpToolCallResponse.clarification(TOOL_ID, exception.userMessage(), elapsedMillis(startedAt));
            }
            return McpToolCallResponse.failure(
                    TOOL_ID,
                    exception.userMessage(),
                    sanitizeError(exception.getMessage()),
                    elapsedMillis(startedAt)
            );
        }
    }

    private String resolveTrackingNo(McpToolCallRequest request) {
        Object explicitTrackingNo = request.arguments().get("trackingNo");
        if (explicitTrackingNo instanceof String value && !value.isBlank()) {
            return normalizeTrackingNo(value);
        }
        Matcher matcher = TRACKING_NO_PATTERN.matcher(request.query());
        while (matcher.find()) {
            String candidate = matcher.group();
            if (!looksLikePlainWord(candidate) && !looksLikePhone(candidate)) {
                return normalizeTrackingNo(candidate);
            }
        }
        return null;
    }

    private String resolveCarrierCode(McpToolCallRequest request, String trackingNo) {
        String explicit = firstNonBlank(
                resolveStringArgument(request, "carrierCode"),
                resolveStringArgument(request, "com"),
                resolveStringArgument(request, "carrier")
        );
        if (!explicit.isBlank()) {
            return carrierCodeFromText(explicit, true);
        }
        String query = request.query();
        String queryCarrierCode = carrierCodeFromText(query, false);
        return !queryCarrierCode.isBlank() ? queryCarrierCode : carrierCodeFromTrackingNo(trackingNo);
    }

    private String resolveCarrierName(McpToolCallRequest request) {
        return firstNonBlank(
                resolveStringArgument(request, "carrierName"),
                resolveStringArgument(request, "carrier")
        );
    }

    private String resolvePhone(McpToolCallRequest request) {
        String explicit = firstNonBlank(
                resolveStringArgument(request, "phone"),
                resolveStringArgument(request, "mobile"),
                resolveStringArgument(request, "tel")
        );
        if (!explicit.isBlank()) {
            return explicit;
        }
        Matcher matcher = PHONE_PATTERN.matcher(request.query());
        return matcher.find() ? matcher.group() : "";
    }

    private String resolveStringArgument(McpToolCallRequest request, String name) {
        Object value = request.arguments().get(name);
        return value instanceof String text ? text.trim() : "";
    }

    private String carrierCodeFromText(String text, boolean allowRawCode) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.contains("顺丰") || lower.equals("sf")) {
            return "shunfeng";
        }
        if (value.contains("中通") || lower.equals("zto")) {
            return "zhongtong";
        }
        if (value.contains("圆通") || lower.equals("yt")) {
            return "yuantong";
        }
        if (value.contains("申通") || lower.equals("sto")) {
            return "shentong";
        }
        if (value.contains("韵达") || lower.equals("yd")) {
            return "yunda";
        }
        if (value.contains("京东") || lower.equals("jd")) {
            return "jd";
        }
        if (value.contains("邮政") || lower.equals("ems")) {
            return "ems";
        }
        if (value.contains("极兔")) {
            return "jtexpress";
        }
        if (value.contains("德邦")) {
            return "debangwuliu";
        }
        return allowRawCode && lower.matches("[a-z0-9_]+") ? lower : "";
    }

    private String carrierCodeFromTrackingNo(String trackingNo) {
        if (trackingNo.startsWith("SF")) {
            return "shunfeng";
        }
        if (trackingNo.startsWith("ZTO")) {
            return "zhongtong";
        }
        if (trackingNo.startsWith("YT")) {
            return "yuantong";
        }
        if (trackingNo.startsWith("STO")) {
            return "shentong";
        }
        if (trackingNo.startsWith("YD")) {
            return "yunda";
        }
        if (trackingNo.startsWith("JD")) {
            return "jd";
        }
        if (trackingNo.startsWith("EMS")) {
            return "ems";
        }
        return "";
    }

    private boolean looksLikePlainWord(String candidate) {
        return candidate.matches("[A-Za-z]+") || candidate.length() < 8;
    }

    private boolean looksLikePhone(String candidate) {
        return candidate.matches("1[3-9]\\d{9}");
    }

    private String normalizeTrackingNo(String trackingNo) {
        return trackingNo.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> toDataMap(LogisticsQueryResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        putIfNotBlank(data, "trackingNo", result.trackingNo());
        putIfNotBlank(data, "carrierCode", result.carrierCode());
        putIfNotBlank(data, "carrier", result.carrierName());
        putIfNotBlank(data, "state", result.state());
        putIfNotBlank(data, "status", result.stateName());
        data.put("signed", result.signed());
        putIfNotBlank(data, "currentLocation", result.currentLocation());
        putIfNotBlank(data, "latestTime", result.latestTime());
        putIfNotBlank(data, "estimatedDelivery", result.estimatedDelivery());
        List<Map<String, String>> events = new ArrayList<>();
        for (LogisticsEvent event : result.events()) {
            Map<String, String> item = new LinkedHashMap<>();
            putIfNotBlank(item, "time", event.time());
            putIfNotBlank(item, "context", event.context());
            putIfNotBlank(item, "location", event.location());
            putIfNotBlank(item, "status", event.status());
            events.add(item);
        }
        data.put("events", events);
        return data;
    }

    private void putIfNotBlank(Map<String, Object> data, String key, Object value) {
        if (value instanceof String text) {
            if (!text.isBlank()) {
                data.put(key, text);
            }
            return;
        }
        if (value != null) {
            data.put(key, value);
        }
    }

    private void putIfNotBlank(Map<String, String> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    private String formatMessage(LogisticsQueryResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("快递单号 ").append(result.trackingNo()).append(" 当前状态：").append(firstNonBlank(result.stateName(), "未知")).append("。\n");
        appendLine(builder, "承运商", result.carrierName());
        appendLine(builder, "最新位置", result.currentLocation());
        appendLine(builder, "更新时间", result.latestTime());
        appendLine(builder, "预计送达", result.estimatedDelivery());
        if (result.signed()) {
            builder.append("签收状态：已签收\n");
        }
        if (result.events().isEmpty()) {
            builder.append("\n暂时没有详细轨迹。");
            return builder.toString();
        }
        builder.append("\n");
        builder.append("物流轨迹：");
        int limit = Math.min(result.events().size(), 8);
        for (int index = 0; index < limit; index++) {
            LogisticsEvent event = result.events().get(index);
            builder.append("\n")
                    .append(index + 1)
                    .append(". ")
                    .append(firstNonBlank(event.time(), "未知时间"))
                    .append(" ")
                    .append(firstNonBlank(event.context(), event.location(), "暂无描述"));
        }
        if (result.events().size() > limit) {
            builder.append("\n").append("... 还有 ").append(result.events().size() - limit).append(" 条轨迹已省略");
        }
        return builder.toString();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append("：").append(value).append("\n");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String sanitizeError(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
