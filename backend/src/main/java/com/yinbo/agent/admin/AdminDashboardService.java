package com.yinbo.agent.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.admin.dto.AdminDashboardResponse;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.auth.mapper.AuthUserMapper;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ChatMessageEntity;
import com.yinbo.agent.chat.mapper.ChatConversationMapper;
import com.yinbo.agent.chat.mapper.ChatMessageMapper;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {

    private final AuthUserMapper authUserMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatConversationMapper chatConversationMapper;
    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(
            AuthUserMapper authUserMapper,
            ChatMessageMapper chatMessageMapper,
            ChatConversationMapper chatConversationMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.authUserMapper = authUserMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatConversationMapper = chatConversationMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminDashboardResponse dashboard() {
        long activeUserCount = nullToZero(authUserMapper.selectCount(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getStatus, 1)
                .ge(AuthUser::getLastLoginAt, LocalDateTime.now().minusDays(1))));
        long messageCount = nullToZero(chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessageEntity>()));
        long conversationCount = nullToZero(chatConversationMapper.selectCount(new LambdaQueryWrapper<ChatConversation>()));
        long trafficCharacterCount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(length(content)), 0) FROM chat_message",
                Long.class
        );

        return new AdminDashboardResponse(
                activeUserCount,
                messageCount,
                conversationCount,
                trafficCharacterCount,
                null,
                null,
                null
        );
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
