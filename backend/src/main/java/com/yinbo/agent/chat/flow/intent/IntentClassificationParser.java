package com.yinbo.agent.chat.flow.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.NodeScore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
// 意图分类模型输出解析器。
public class IntentClassificationParser {

    private static final Logger log = LoggerFactory.getLogger(IntentClassificationParser.class);

    private final ObjectMapper objectMapper;

    // 注入 JSON 工具。
    public IntentClassificationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 解析模型返回的 JSON 数组或 results 包装对象。
    public List<NodeScore> parse(String rawContent, Map<String, IntentNode> nodeById) {
        if (rawContent == null || rawContent.isBlank() || nodeById == null || nodeById.isEmpty()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFence(rawContent));
            JsonNode array = root.isArray() ? root : root.get("results");
            if (array == null || !array.isArray()) {
                return List.of();
            }
            List<NodeScore> scores = new ArrayList<>();
            for (JsonNode item : array) {
                NodeScore score = parseItem(item, nodeById);
                if (score != null) {
                    scores.add(score);
                }
            }
            scores.sort(Comparator.comparingDouble(NodeScore::score).reversed());
            return scores;
        } catch (Exception exception) {
            log.warn("event=intent_classification_parse_failed type={} message={}",
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()));
            return List.of();
        }
    }

    private NodeScore parseItem(JsonNode item, Map<String, IntentNode> nodeById) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String id = textValue(firstPresent(item, "id", "nodeId", "intent_id", "intentId"));
        if (id == null || id.isBlank()) {
            return null;
        }
        IntentNode node = nodeById.get(id);
        if (node == null) {
            return null;
        }
        Double score = numberValue(item.get("score"));
        if (score == null) {
            return null;
        }
        String reason = textValue(item.get("reason"));
        return new NodeScore(node, clampScore(score), reason, "LLM");
    }

    private JsonNode firstPresent(JsonNode item, String... names) {
        for (String name : names) {
            JsonNode node = item.get(name);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private String stripMarkdownFence(String content) {
        String text = content.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return text.substring(firstLineEnd + 1, lastFence).trim();
        }
        return text.replace("```json", "").replace("```", "").trim();
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText(null);
    }

    private Double numberValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double clampScore(double value) {
        if (value < 0D) {
            return 0D;
        }
        if (value > 1D) {
            return 1D;
        }
        return value;
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
