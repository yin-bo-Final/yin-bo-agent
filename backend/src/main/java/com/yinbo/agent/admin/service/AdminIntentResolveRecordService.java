package com.yinbo.agent.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.admin.dto.IntentResolveRecordResponse;
import com.yinbo.agent.admin.dto.PageResponse;
import com.yinbo.agent.chat.entity.ChatIntentResolveRecord;
import com.yinbo.agent.chat.mapper.ChatIntentResolveRecordMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
// 管理后台意图识别记录查询服务。
public class AdminIntentResolveRecordService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatIntentResolveRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    // 注入意图识别记录 Mapper 和 JSON 工具。
    public AdminIntentResolveRecordService(
            ChatIntentResolveRecordMapper recordMapper,
            ObjectMapper objectMapper
    ) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    // 分页查询意图识别记录。
    public PageResponse<IntentResolveRecordResponse> page(
            Integer page,
            Integer pageSize,
            String keyword,
            String outcome,
            String status,
            String ambiguous,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        long total = recordMapper.selectCount(buildQuery(keyword, outcome, status, ambiguous, startAt, endAt));
        long pages = total == 0 ? 0 : (long) Math.ceil(total / (double) safePageSize);
        long offset = (long) (safePage - 1) * safePageSize;
        List<IntentResolveRecordResponse> records = recordMapper.selectList(buildQuery(keyword, outcome, status, ambiguous, startAt, endAt)
                        .orderByDesc("created_at")
                        .orderByDesc("id")
                        .last("LIMIT " + safePageSize + " OFFSET " + offset))
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(safePage, safePageSize, total, pages, records);
    }

    private QueryWrapper<ChatIntentResolveRecord> buildQuery(
            String keyword,
            String outcome,
            String status,
            String ambiguous,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        QueryWrapper<ChatIntentResolveRecord> query = new QueryWrapper<>();
        String cleanOutcome = normalizeFilter(outcome == null || outcome.isBlank() ? status : outcome);
        if (cleanOutcome != null) {
            query.eq("outcome", cleanOutcome);
        }
        Boolean ambiguousValue = parseBooleanFilter(ambiguous);
        if (ambiguousValue != null) {
            query.eq("ambiguous", ambiguousValue);
        }
        if (startAt != null) {
            query.ge("created_at", startAt);
        }
        if (endAt != null) {
            query.le("created_at", endAt);
        }
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        if (!cleanKeyword.isEmpty()) {
            String likeKeyword = "%" + cleanKeyword.toLowerCase() + "%";
            query.and(wrapper -> {
                wrapper.apply("LOWER(original_query) LIKE {0}", likeKeyword)
                        .or().apply("LOWER(COALESCE(normalized_query, '')) LIKE {0}", likeKeyword)
                        .or().apply("LOWER(COALESCE(rewritten_query, '')) LIKE {0}", likeKeyword)
                        .or().apply("LOWER(COALESCE(model_id, '')) LIKE {0}", likeKeyword)
                        .or().apply("LOWER(COALESCE(fallback_reason, '')) LIKE {0}", likeKeyword)
                        .or().apply("LOWER(COALESCE(error_message, '')) LIKE {0}", likeKeyword);
                Long idKeyword = parseLong(cleanKeyword);
                if (idKeyword != null) {
                    wrapper.or().eq("id", idKeyword)
                            .or().eq("conversation_id", idKeyword)
                            .or().eq("user_id", idKeyword)
                            .or().eq("user_message_id", idKeyword);
                }
            });
        }
        return query;
    }

    private IntentResolveRecordResponse toResponse(ChatIntentResolveRecord record) {
        return new IntentResolveRecordResponse(
                stringId(record.getId()),
                stringId(record.getConversationId()),
                stringId(record.getUserId()),
                stringId(record.getUserMessageId()),
                record.getOriginalQuery(),
                record.getNormalizedQuery(),
                record.getRewrittenQuery(),
                readJson(record.getSubQuestionsJson()),
                readJson(record.getIntentsJson()),
                readJson(record.getSelectedNodesJson()),
                readJson(record.getSubQuestionIntentsJson()),
                record.getModelId(),
                record.getAmbiguous(),
                record.getGuidanceQuestion(),
                record.getOutcome(),
                record.getFallbackReason(),
                record.getSuccess(),
                record.getErrorMessage(),
                record.getDurationMs(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            return objectMapper.createArrayNode();
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private Boolean parseBooleanFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "YES".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "NO".equalsIgnoreCase(value)) {
            return false;
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringId(Long value) {
        return value == null ? null : value.toString();
    }
}
