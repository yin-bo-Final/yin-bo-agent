package com.yinbo.agent.chat.flow.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.entity.ChatQueryRewriteRecord;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.mapper.ChatQueryRewriteRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
// 查询改写记录落库服务。
public class QueryRewriteRecordService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteRecordService.class);

    private final ChatQueryRewriteRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    // 注入查询改写记录 Mapper 和 JSON 工具。
    public QueryRewriteRecordService(ChatQueryRewriteRecordMapper recordMapper, ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    // 保存查询改写流水线记录，失败不影响主流程。
    public void record(
            ChatExecutionContext ctx,
            QueryRewriteResult result,
            String promptVersion,
            String rawModelResponse,
            String errorMessage,
            long durationMs
    ) {
        try {
            ChatQueryRewriteRecord record = new ChatQueryRewriteRecord();
            record.setConversationId(ctx.conversation().getId());
            record.setUserId(ctx.authUser().getId());
            record.setUserMessageId(ctx.userMessage() == null ? null : ctx.userMessage().getId());
            record.setOriginalQuery(ctx.originalQuery());
            record.setNormalizedQuery(result.normalizedQuery());
            record.setRewrittenQuery(result.rewrite());
            record.setSubQuestionsJson(toJson(result.subQuestions()));
            record.setMatchedTermsJson(toJson(result.matchedTerms()));
            record.setModelId(ctx.model() == null ? null : ctx.model().id());
            record.setPromptVersion(promptVersion);
            record.setSourceType(result.sourceType());
            record.setFallbackReason(result.fallbackReason());
            record.setSuccess(result.success());
            record.setErrorMessage(truncate(errorMessage, 512));
            record.setRawModelResponse(truncate(rawModelResponse, 8_000));
            record.setDurationMs(durationMs);
            recordMapper.insert(record);
        } catch (Exception exception) {
            log.warn(
                    "event=query_rewrite_record_failed conversationId={} type={} message={}",
                    ctx.conversation() == null ? "-" : ctx.conversation().getConversationNo(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
