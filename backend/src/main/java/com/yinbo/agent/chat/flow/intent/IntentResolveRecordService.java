package com.yinbo.agent.chat.flow.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yinbo.agent.chat.entity.ChatIntentResolveRecord;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.context.ChatIntentType;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentResolveResult;
import com.yinbo.agent.chat.flow.intent.model.NodeScore;
import com.yinbo.agent.chat.flow.intent.model.SubQuestionIntent;
import com.yinbo.agent.chat.flow.query.QueryRewriteResult;
import com.yinbo.agent.chat.mapper.ChatIntentResolveRecordMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
// 意图识别记录落库服务。
public class IntentResolveRecordService {

    private static final Logger log = LoggerFactory.getLogger(IntentResolveRecordService.class);

    private final ChatIntentResolveRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    // 注入意图识别记录 Mapper 和 JSON 工具。
    public IntentResolveRecordService(ChatIntentResolveRecordMapper recordMapper, ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    // 保存意图识别流水线记录，失败不影响主流程。
    public void record(ChatExecutionContext ctx, IntentResolveResult result, long durationMs) {
        try {
            QueryRewriteResult rewriteResult = ctx.rewriteResult();
            ChatIntentResolveRecord record = new ChatIntentResolveRecord();
            record.setConversationId(ctx.conversation().getId());
            record.setUserId(ctx.authUser().getId());
            record.setUserMessageId(ctx.userMessage() == null ? null : ctx.userMessage().getId());
            record.setOriginalQuery(ctx.originalQuery());
            record.setNormalizedQuery(rewriteResult == null ? null : rewriteResult.normalizedQuery());
            record.setRewrittenQuery(ctx.rewrittenQuery());
            record.setSubQuestionsJson(toJson(result.subQuestionIntents().stream()
                    .map(SubQuestionIntent::subQuestion)
                    .toList()));
            record.setIntentsJson(toJson(ctx.intents().stream().map(ChatIntentType::name).toList()));
            record.setSelectedNodesJson(toJson(result.selectedNodeScores().stream()
                    .map(this::toScoreSnapshot)
                    .toList()));
            record.setSubQuestionIntentsJson(toJson(result.subQuestionIntents().stream()
                    .map(this::toSubQuestionSnapshot)
                    .toList()));
            record.setModelId(ctx.model() == null ? null : ctx.model().id());
            record.setAmbiguous(result.ambiguous());
            record.setGuidanceQuestion(result.guidanceQuestion());
            record.setOutcome(result.outcome().name());
            record.setFallbackReason(result.fallbackReason());
            record.setSuccess(result.success());
            record.setErrorMessage(null);
            record.setDurationMs(durationMs);
            recordMapper.insert(record);
        } catch (Exception exception) {
            log.warn(
                    "event=intent_resolve_record_failed conversationId={} type={} message={}",
                    conversationId(ctx),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
        }
    }

    private SubQuestionSnapshot toSubQuestionSnapshot(SubQuestionIntent subQuestionIntent) {
        return new SubQuestionSnapshot(
                subQuestionIntent.subQuestion(),
                subQuestionIntent.nodeScores().stream().map(this::toScoreSnapshot).toList()
        );
    }

    private NodeScoreSnapshot toScoreSnapshot(NodeScore score) {
        IntentNode node = score.node();
        return new NodeScoreSnapshot(
                node == null ? null : node.getId(),
                node == null ? null : node.getFullPath(),
                node == null || node.getKind() == null ? null : node.getKind().name(),
                score.score(),
                score.reason(),
                score.source()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String conversationId(ChatExecutionContext ctx) {
        return ctx.conversation() == null ? "-" : ctx.conversation().getConversationNo();
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    private record SubQuestionSnapshot(String subQuestion, List<NodeScoreSnapshot> nodeScores) {
    }

    private record NodeScoreSnapshot(
            String nodeCode,
            String path,
            String kind,
            double score,
            String reason,
            String source
    ) {
    }
}
