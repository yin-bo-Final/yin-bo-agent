package com.yinbo.agent.chat.flow.intent.model;

// 意图叶子节点命中后的处理类型。
public enum IntentKind {
    KB,
    SYSTEM,
    MCP;

    // 规范化意图类型。
    public static IntentKind from(String value) {
        if (value == null || value.isBlank()) {
            return KB;
        }
        try {
            return IntentKind.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return KB;
        }
    }
}
