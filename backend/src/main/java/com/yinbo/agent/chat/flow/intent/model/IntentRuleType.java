package com.yinbo.agent.chat.flow.intent.model;

// 意图规则类型。
public enum IntentRuleType {
    STRONG,
    WEAK;

    // 规范化规则类型。
    public static IntentRuleType from(String value) {
        if (value == null || value.isBlank()) {
            return STRONG;
        }
        try {
            return IntentRuleType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return STRONG;
        }
    }
}
