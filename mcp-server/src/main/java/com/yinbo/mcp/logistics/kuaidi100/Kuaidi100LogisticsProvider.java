package com.yinbo.mcp.logistics.kuaidi100;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yinbo.mcp.config.LogisticsProperties;
import com.yinbo.mcp.logistics.LogisticsEvent;
import com.yinbo.mcp.logistics.LogisticsProvider;
import com.yinbo.mcp.logistics.LogisticsProviderException;
import com.yinbo.mcp.logistics.LogisticsQueryRequest;
import com.yinbo.mcp.logistics.LogisticsQueryResult;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
// 快递100实时查询适配器。
public class Kuaidi100LogisticsProvider implements LogisticsProvider {

    private static final Set<String> PHONE_REQUIRED_COMPANIES = Set.of(
            "shunfeng",
            "shunfengkuaiyun",
            "zhongtong"
    );
    private static final Map<String, String> STATE_NAMES = Map.ofEntries(
            Map.entry("0", "在途"),
            Map.entry("1", "揽收"),
            Map.entry("2", "疑难"),
            Map.entry("3", "签收"),
            Map.entry("4", "退签"),
            Map.entry("5", "派件"),
            Map.entry("6", "退回"),
            Map.entry("7", "转投"),
            Map.entry("8", "清关"),
            Map.entry("14", "拒签")
    );
    private static final Map<String, String> CARRIER_NAMES = Map.ofEntries(
            Map.entry("shunfeng", "顺丰速运"),
            Map.entry("zhongtong", "中通快递"),
            Map.entry("yuantong", "圆通速递"),
            Map.entry("shentong", "申通快递"),
            Map.entry("yunda", "韵达快递"),
            Map.entry("jd", "京东物流"),
            Map.entry("ems", "中国邮政 EMS"),
            Map.entry("jtexpress", "极兔速递"),
            Map.entry("debangwuliu", "德邦物流"),
            Map.entry("huitongkuaidi", "百世快递")
    );

    private final LogisticsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public Kuaidi100LogisticsProvider(LogisticsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = properties.getRequestTimeout() == null ? Duration.ofSeconds(10) : properties.getRequestTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public LogisticsQueryResult query(LogisticsQueryRequest request) {
        ensureConfigured();
        Carrier carrier = resolveCarrier(request);
        String phone = normalize(request.phone());
        if (PHONE_REQUIRED_COMPANIES.contains(carrier.code()) && isBlank(phone)) {
            throw LogisticsProviderException.clarification("该快递公司查询需要收/寄件手机号或后四位，请补充手机号后再查。");
        }

        JsonNode response = queryTracking(request, carrier, phone);
        return toResult(request.trackingNo(), carrier, response);
    }

    private void ensureConfigured() {
        LogisticsProperties.Kuaidi100 config = properties.getKuaidi100();
        if (isBlank(config.getKey()) || isBlank(config.getCustomer())) {
            throw LogisticsProviderException.failure(
                    "物流查询服务还没有配置快递100授权信息，请先在 local-secrets.yml 配置 KUAIDI100_KEY 和 KUAIDI100_CUSTOMER。",
                    "kuaidi100 credentials missing"
            );
        }
    }

    private Carrier resolveCarrier(LogisticsQueryRequest request) {
        String explicitCode = normalize(request.carrierCode());
        if (!isBlank(explicitCode)) {
            return new Carrier(explicitCode, firstNonBlank(request.carrierName(), CARRIER_NAMES.get(explicitCode), explicitCode));
        }
        Carrier autoDetected = autoDetectCarrier(request.trackingNo());
        if (autoDetected != null) {
            return autoDetected;
        }
        throw LogisticsProviderException.clarification("暂时没识别出快递公司，请补充快递公司名称或编码后再查。");
    }

    private Carrier autoDetectCarrier(String trackingNo) {
        LogisticsProperties.Kuaidi100 config = properties.getKuaidi100();
        URI uri = appendQuery(config.getAutoNumberUrl(), Map.of(
                "num", trackingNo,
                "key", config.getKey()
        ));
        JsonNode response = sendJson(HttpRequest.newBuilder(uri)
                .timeout(effectiveTimeout())
                .GET()
                .build(), "kuaidi100 auto number");
        if (response.isArray() && !response.isEmpty()) {
            JsonNode first = response.get(0);
            String code = normalize(text(first, "comCode"));
            if (!isBlank(code)) {
                return new Carrier(code, firstNonBlank(text(first, "name"), CARRIER_NAMES.get(code), code));
            }
        }
        if (response.isObject()) {
            String code = text(response, "returnCode");
            if ("201".equals(code)) {
                throw LogisticsProviderException.clarification("这个快递单号看起来不太规范，请确认后再发我一次。");
            }
            if ("601".equals(code) || "701".equals(code)) {
                throw LogisticsProviderException.failure("物流查询服务的快递100授权不可用，请检查 KUAIDI100_KEY。", "kuaidi100 auto number code=" + code);
            }
        }
        return null;
    }

    private JsonNode queryTracking(LogisticsQueryRequest request, Carrier carrier, String phone) {
        LogisticsProperties.Kuaidi100 config = properties.getKuaidi100();
        ObjectNode param = objectMapper.createObjectNode();
        param.put("com", carrier.code());
        param.put("num", request.trackingNo());
        putIfNotBlank(param, "phone", phone);
        putIfNotBlank(param, "from", request.from());
        putIfNotBlank(param, "to", request.to());
        putIfNotBlank(param, "resultv2", config.getResultV2());
        param.put("show", "0");
        putIfNotBlank(param, "order", config.getOrder());
        putIfNotBlank(param, "lang", config.getLang());

        String paramText;
        try {
            paramText = objectMapper.writeValueAsString(param);
        } catch (JsonProcessingException exception) {
            throw LogisticsProviderException.failure("物流查询参数序列化失败，请稍后重试。", exception.getMessage(), exception);
        }

        String sign = md5Upper(paramText + config.getKey() + config.getCustomer());
        String body = formBody(Map.of(
                "customer", config.getCustomer(),
                "sign", sign,
                "param", paramText
        ));
        HttpRequest requestMessage = HttpRequest.newBuilder(config.getQueryUrl())
                .timeout(effectiveTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        JsonNode response = sendJson(requestMessage, "kuaidi100 query");
        validateQueryResponse(response);
        return response;
    }

    private LogisticsQueryResult toResult(String trackingNo, Carrier carrier, JsonNode response) {
        List<LogisticsEvent> events = new ArrayList<>();
        JsonNode data = response.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                events.add(new LogisticsEvent(
                        firstNonBlank(text(item, "ftime"), text(item, "time")),
                        text(item, "context"),
                        firstNonBlank(text(item, "location"), text(item, "areaName")),
                        text(item, "status")
                ));
            }
        }
        JsonNode latest = events.isEmpty() ? null : data.get(0);
        String state = text(response, "state");
        String stateName = firstNonBlank(
                latest == null ? null : text(latest, "status"),
                STATE_NAMES.get(state),
                state
        );
        String currentLocation = firstNonBlank(
                latest == null ? null : text(latest, "location"),
                routeInfoName(response, "cur"),
                latest == null ? null : text(latest, "areaName")
        );
        return new LogisticsQueryResult(
                firstNonBlank(text(response, "nu"), trackingNo),
                firstNonBlank(text(response, "com"), carrier.code()),
                carrier.name(),
                state,
                stateName,
                "1".equals(text(response, "ischeck")) || "3".equals(state),
                currentLocation,
                events.isEmpty() ? "" : events.get(0).time(),
                text(response, "arrivalTime"),
                events
        );
    }

    private void validateQueryResponse(JsonNode response) {
        if (response.path("result").isBoolean() && !response.path("result").asBoolean()) {
            handleError(text(response, "returnCode"), text(response, "message"));
        }
        String status = text(response, "status");
        if (!isBlank(status) && !"200".equals(status)) {
            handleError(status, text(response, "message"));
        }
        if (!response.path("data").isArray()) {
            handleError(text(response, "returnCode"), firstNonBlank(text(response, "message"), "物流平台没有返回轨迹数据"));
        }
    }

    private void handleError(String code, String message) {
        String safeMessage = firstNonBlank(message, "物流平台暂时没有返回结果");
        if ("408".equals(code)) {
            throw LogisticsProviderException.clarification("该快递公司查询需要收/寄件手机号或后四位，请补充手机号后再查。");
        }
        if ("400".equals(code)) {
            throw LogisticsProviderException.clarification("没有找到对应的快递公司，请补充快递公司名称或确认单号是否正确。");
        }
        if ("500".equals(code) || "201".equals(code)) {
            throw LogisticsProviderException.clarification("暂时没有查到这张单号的物流信息，请确认单号和快递公司后再试。");
        }
        throw LogisticsProviderException.failure("物流平台返回错误：" + safeMessage, "kuaidi100 error code=" + code + ", message=" + safeMessage);
    }

    private JsonNode sendJson(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw LogisticsProviderException.failure(
                        "物流平台暂时不可用，请稍后重试。",
                        operation + " http status=" + response.statusCode()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw LogisticsProviderException.failure("物流平台暂时不可用，请稍后重试。", operation + " io error", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw LogisticsProviderException.failure("物流查询被中断，请稍后重试。", operation + " interrupted", exception);
        }
    }

    private URI appendQuery(URI baseUri, Map<String, String> params) {
        StringBuilder builder = new StringBuilder(baseUri.toString());
        builder.append(baseUri.getRawQuery() == null ? "?" : "&");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            builder.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
            first = false;
        }
        return URI.create(builder.toString());
    }

    private String formBody(Map<String, String> params) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            parts.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
        }
        return String.join("&", parts);
    }

    private String md5Upper(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString().toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw LogisticsProviderException.failure("物流查询签名失败，请稍后重试。", exception.getMessage(), exception);
        }
    }

    private Duration effectiveTimeout() {
        return properties.getRequestTimeout() == null ? Duration.ofSeconds(10) : properties.getRequestTimeout();
    }

    private void putIfNotBlank(ObjectNode node, String field, String value) {
        if (!isBlank(value)) {
            node.put(field, value.trim());
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return "";
        }
        return node.path(field).asText("");
    }

    private String routeInfoName(JsonNode response, String field) {
        return text(response.path("routeInfo").path(field), "name");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private record Carrier(String code, String name) {
    }
}
