package com.yinbo.agent.chat.flow.clarification;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import org.springframework.stereotype.Service;

@Service
// 会话歧义引导服务。
public class ClarificationService {

    // 返回需要发给用户的澄清问题，当前默认不触发。
    public String guidanceMessage(ChatExecutionContext ctx) {
        if (!ctx.ambiguous() || ctx.guidanceQuestion() == null || ctx.guidanceQuestion().isBlank()) {
            return null;
        }
        return ctx.guidanceQuestion();
    }
}
