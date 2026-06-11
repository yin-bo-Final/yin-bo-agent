package com.yinbo.agent.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.admin.dto.PageResponse;
import com.yinbo.agent.admin.dto.QueryRewriteRecordResponse;
import com.yinbo.agent.chat.entity.ChatQueryRewriteRecord;
import com.yinbo.agent.chat.mapper.ChatQueryRewriteRecordMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
// 管理后台查询改写记录查询服务。
public class AdminQueryRewriteRecordService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatQueryRewriteRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    // 注入查询改写记录 Mapper 和 JSON 工具。
    public AdminQueryRewriteRecordService(
            ChatQueryRewriteRecordMapper recordMapper,
            ObjectMapper objectMapper
    ) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    // 分页查询查询改写记录。
    public PageResponse<QueryRewriteRecordResponse> page(
            Integer page,
            Integer pageSize,
            String keyword,
            String sourceType,
            String success,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<ChatQueryRewriteRecord> countQuery = buildQuery(keyword, sourceType, success, startAt, endAt);
        long total = recordMapper.selectCount(countQuery);
        long pages = total == 0 ? 0 : (long) Math.ceil(total / (double) safePageSize);
        long offset = (long) (safePage - 1) * safePageSize;
        List<QueryRewriteRecordResponse> records = recordMapper.selectList(buildQuery(keyword, sourceType, success, startAt, endAt)
                        .orderByDesc("created_at")
                        .orderByDesc("id")
                        .last("LIMIT " + safePageSize + " OFFSET " + offset))
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(safePage, safePageSize, total, pages, records);
    }

    private QueryWrapper<ChatQueryRewriteRecord> buildQuery(
            String keyword,
            String sourceType,
            String success,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        QueryWrapper<ChatQueryRewriteRecord> query = new QueryWrapper<>();
        String cleanSourceType = normalizeFilter(sourceType);
        if (cleanSourceType != null) {
            query.eq("source_type", cleanSourceType);
        }
        Boolean successValue = parseBooleanFilter(success);
        if (successValue != null) {
            query.eq("success", successValue);
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
                        .or().apply("LOWER(COALESCE(prompt_version, '')) LIKE {0}", likeKeyword)
                        .or().apply("LOWER(COALESCE(source_type, '')) LIKE {0}", likeKeyword)
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

    private QueryRewriteRecordResponse toResponse(ChatQueryRewriteRecord record) {
        return new QueryRewriteRecordResponse(
                stringId(record.getId()),
                stringId(record.getConversationId()),
                stringId(record.getUserId()),
                stringId(record.getUserMessageId()),
                record.getOriginalQuery(),
                record.getNormalizedQuery(),
                record.getRewrittenQuery(),
                readJson(record.getSubQuestionsJson()),
                readJson(record.getMatchedTermsJson()),
                record.getModelId(),
                record.getPromptVersion(),
                record.getSourceType(),
                record.getFallbackReason(),
                record.getSuccess(),
                record.getErrorMessage(),
                record.getRawModelResponse(),
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
        if ("true".equalsIgnoreCase(value) || "SUCCESS".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "FAILED".equalsIgnoreCase(value)) {
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
