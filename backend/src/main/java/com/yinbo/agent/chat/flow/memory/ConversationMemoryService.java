package com.yinbo.agent.chat.flow.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import com.yinbo.agent.chat.service.ChatMessageCacheService;
import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
// 会话历史记忆加载服务。
public class ConversationMemoryService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageCacheService chatMessageCacheService;

    // 注入消息 Mapper 和会话消息缓存服务。
    public ConversationMemoryService(
            ChatMessageMapper chatMessageMapper,
            ChatMessageCacheService chatMessageCacheService
    ) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageCacheService = chatMessageCacheService;
    }

    // 为当前会话执行上下文加载历史记忆。
    public void load(ChatExecutionContext ctx) {
        ctx.setConversationMessages(load(ctx.authUser().getId(), ctx.conversation().getId()));
    }

    // 加载指定用户指定会话的历史消息。
    public List<CachedChatMessage> load(Long userId, Long conversationId) {
        return chatMessageCacheService.getMessages(
                userId,
                conversationId,
                () -> selectConversationMessages(userId, conversationId)
        );
    }

    // 从数据库查询会话消息。
    private List<ChatMessageEntity> selectConversationMessages(Long userId, Long conversationId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversationId)
                .eq(ChatMessageEntity::getUserId, userId)
                .orderByAsc(ChatMessageEntity::getCreatedAt)
                .orderByAsc(ChatMessageEntity::getId));
    }
}
