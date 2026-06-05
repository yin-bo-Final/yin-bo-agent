package com.yinbo.agent.chat.flow.intent;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.context.ChatIntentType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
// 会话意图识别服务。
public class IntentResolutionService {

    // 识别用户意图，当前默认普通直聊。
    public void resolve(ChatExecutionContext ctx) {
        ctx.setIntents(List.of(ChatIntentType.DIRECT_CHAT));
    }

    // 判断当前会话是否可以直接调用 LLM。
    public boolean isDirectChat(ChatExecutionContext ctx) {
        return ctx.hasIntent(ChatIntentType.DIRECT_CHAT);
    }
}
