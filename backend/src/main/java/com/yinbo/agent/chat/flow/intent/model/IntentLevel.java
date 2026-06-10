package com.yinbo.agent.chat.flow.intent.model;

// 意图树节点层级。
public enum IntentLevel {
    DOMAIN,
    CATEGORY,
    TOPIC;

    // 规范化节点层级。
    public static IntentLevel from(String value) {
        if (value == null || value.isBlank()) {
            return CATEGORY;
        }
        try {
            return IntentLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return CATEGORY;
        }
    }
}
