package com.yinbo.agent.chat.flow.context;

// 会话处理阶段识别出来的用户意图类型。
public enum ChatIntentType {
    DIRECT_CHAT,
    KNOWLEDGE_RAG,
    TOOL_CALL,
    RAG_AND_TOOL,
    CLARIFICATION
}
