package com.yinbo.agent.chat.flow.intent.model;

// 意图规则关键词匹配模式。
public enum IntentRuleMatchMode {
    ANY,
    ALL;

    // 规范化匹配模式。
    public static IntentRuleMatchMode from(String value) {
        if (value == null || value.isBlank()) {
            return ANY;
        }
        try {
            return IntentRuleMatchMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ANY;
        }
    }
}
