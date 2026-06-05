package com.yinbo.agent.chat.flow.retrieval;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import org.springframework.stereotype.Service;

@Service
// 会话多通道检索执行服务。
public class RetrievalExecuteService {

    // 执行知识库和工具检索，当前返回空结果占位。
    public RetrievalContext retrieve(ChatExecutionContext ctx) {
        return RetrievalContext.empty();
    }

    // 生成检索为空时的兜底回复。
    public String emptyRetrievalMessage(RetrievalContext retrievalContext) {
        if (retrievalContext == null || !retrievalContext.isEmpty()) {
            return null;
        }
        return "未检索到与问题相关的文档。";
    }
}
