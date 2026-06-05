package com.yinbo.agent.chat.flow.query;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
// 会话查询改写和子问题拆分服务。
public class QueryRewriteService {

    // 改写用户查询并拆分子问题，当前保留原始问题。
    public void rewrite(ChatExecutionContext ctx) {
        ctx.setRewrittenQuery(ctx.originalQuery());
        ctx.setSubQueries(List.of());
    }
}
