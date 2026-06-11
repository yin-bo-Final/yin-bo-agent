package com.yinbo.agent.chat.flow.query;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.query.pipeline.QueryPipelineConfigService;
import com.yinbo.agent.chat.flow.query.pipeline.QueryPipelineConfigView;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyMatch;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyNormalizationResult;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyNormalizationService;
import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import com.yinbo.ai.api.chat.LLMMessage;
import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
// 会话查询改写、术语统一和子问题拆分服务。
public class QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteService.class);
    private static final String PROMPT_VERSION = "query-rewrite-v1";
    private static final String FALLBACK_TERM_ONLY = "TERM_ONLY";
    private static final String FALLBACK_RULE_SPLIT = "RULE_SPLIT";
    private static final String FALLBACK_BYPASS = "BYPASS";

    private final TerminologyNormalizationService terminologyNormalizationService;
    private final QueryPipelineConfigService pipelineConfigService;
    private final QueryRewriteResultParser resultParser;
    private final QueryRewriteRecordService recordService;
    private final LLMService llmService;

    // 注入术语统一、流水线配置、解析器、记录服务和 LLM 服务。
    public QueryRewriteService(
            TerminologyNormalizationService terminologyNormalizationService,
            QueryPipelineConfigService pipelineConfigService,
            QueryRewriteResultParser resultParser,
            QueryRewriteRecordService recordService,
            LLMService llmService
    ) {
        this.terminologyNormalizationService = terminologyNormalizationService;
        this.pipelineConfigService = pipelineConfigService;
        this.resultParser = resultParser;
        this.recordService = recordService;
        this.llmService = llmService;
    }

    // 改写用户查询并拆分子问题。
    public void rewrite(ChatExecutionContext ctx) {
        long startedAt = System.nanoTime();
        QueryPipelineConfigView config = pipelineConfigService.currentConfig();
        TerminologyNormalizationResult normalization = normalizeTerms(ctx, config);
        String rawModelResponse = null;
        String errorMessage = null;
        QueryRewriteResult result;

        if (!config.llmRewriteEnabled()) {
            result = fallbackResult(normalization, config, "REWRITE_DISABLED");
            finishRewrite(ctx, result, config, null, null, startedAt);
            return;
        }

        try {
            rawModelResponse = callRewriteModel(ctx, normalization.normalizedQuery(), config);
            result = resultParser.parse(rawModelResponse, normalization.normalizedQuery(), normalization.matches());
        } catch (Exception exception) {
            errorMessage = exception.getMessage();
            result = fallbackResult(normalization, config, fallbackReason(exception));
            log.warn(
                    "event=query_rewrite_failed conversationId={} modelId={} reason={} timeoutMs={} type={} message={}",
                    ctx.conversation().getConversationNo(),
                    ctx.model().id(),
                    result.fallbackReason(),
                    config.rewriteTimeoutMs(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
        }

        finishRewrite(ctx, result, config, rawModelResponse, errorMessage, startedAt);
    }

    private TerminologyNormalizationResult normalizeTerms(ChatExecutionContext ctx, QueryPipelineConfigView config) {
        if (!config.terminologyEnabled()) {
            String originalQuery = ctx.originalQuery() == null ? "" : ctx.originalQuery();
            return new TerminologyNormalizationResult(originalQuery, originalQuery, List.of());
        }
        return terminologyNormalizationService.normalize(ctx.originalQuery());
    }

    private String callRewriteModel(
            ChatExecutionContext ctx,
            String normalizedQuery,
            QueryPipelineConfigView config
    ) throws Exception {
        List<LLMMessage> messages = List.of(
                new LLMMessage("system", rewriteSystemPrompt()),
                new LLMMessage("user", rewriteUserPrompt(ctx, normalizedQuery, config.rewriteContextTurns()))
        );
        LLMRequest request = new LLMRequest(ctx.model().id(), false, messages);
        CompletableFuture<LLMResponse> future = CompletableFuture.supplyAsync(() -> llmService.chat(request));
        LLMResponse response;
        try {
            response = future.get(config.rewriteTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            future.cancel(true);
            throw exception;
        }
        String content = response == null ? null : response.content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("查询改写模型返回空内容");
        }
        return content.trim();
    }

    private String rewriteSystemPrompt() {
        return """
                # 角色
                你是 RAG 检索前的查询改写器。你只负责查询改写和问题拆分，不回答用户问题。

                # 任务
                根据会话上下文，将用户当前问题改写成语义完整、适合检索的自然语言查询，并判断是否需要拆分为多个子问题。

                # 改写规则
                - 保留专有名词原写法，例如系统名、产品名、模块名、类名、方法名。
                - 保留关键限制，例如时间范围、环境、终端类型、角色身份、业务场景。
                - 可删除礼貌用语、回答格式要求、无关身份描述。
                - 只补全上下文中明确可确认的指代，例如“这个、上面、刚才、它”。
                - 不得添加原文或上下文没有的条件、维度、假设。
                - 不得引入“方面、维度、角度”等枚举词，除非原文已有。
                - 保持原问题语言；问题已清晰时，少改或不改。

                # 拆分规则
                - 只在多个问号、显式列举、分号、换行分隔，或“分别”指向多个明确对象时拆分。
                - 抽象对比、笼统询问、不确定时，不拆分。
                - 不拆分时，sub_questions 只包含 1 条，且必须与 rewrite 完全一致。
                - 拆分时，每个子问题都要语义完整，并尽量保持原文表述。

                # 输出格式
                严格返回 JSON，不要 Markdown，不要额外文字：
                {
                  "rewrite": "改写后的查询",
                  "should_split": false,
                  "sub_questions": ["改写后的查询"]
                }
                """;
    }

    private String rewriteUserPrompt(ChatExecutionContext ctx, String normalizedQuery, int recentTurns) {
        String summary = ctx.memorySummary() == null || ctx.memorySummary().getSummaryContent() == null
                ? "暂无"
                : ctx.memorySummary().getSummaryContent();
        return """
                # 会话上下文
                历史摘要：
                %s

                最近对话：
                %s

                # 用户当前问题
                %s
                """.formatted(summary, formatRecentTurns(ctx, recentTurns), normalizedQuery);
    }

    private String formatRecentTurns(ChatExecutionContext ctx, int recentTurns) {
        Long currentUserMessageId = ctx.userMessage() == null ? null : ctx.userMessage().getId();
        List<CachedChatMessage> historyMessages = ctx.conversationMessages().stream()
                .filter(message -> message != null && message.content() != null && !message.content().isBlank())
                .filter(message -> currentUserMessageId == null || message.id() == null || message.id() < currentUserMessageId)
                .filter(message -> "user".equalsIgnoreCase(message.role()) || "assistant".equalsIgnoreCase(message.role()))
                .sorted(Comparator.comparing(CachedChatMessage::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<List<CachedChatMessage>> turns = buildTurns(historyMessages);
        if (turns.isEmpty()) {
            return "暂无";
        }
        int startIndex = Math.max(0, turns.size() - Math.max(1, recentTurns));
        StringBuilder builder = new StringBuilder();
        for (List<CachedChatMessage> turn : turns.subList(startIndex, turns.size())) {
            for (CachedChatMessage message : turn) {
                builder.append(message.role()).append(": ").append(message.content()).append('\n');
            }
            builder.append("---\n");
        }
        return builder.toString().trim();
    }

    private List<List<CachedChatMessage>> buildTurns(List<CachedChatMessage> messages) {
        List<List<CachedChatMessage>> turns = new ArrayList<>();
        int index = 0;
        while (index < messages.size()) {
            CachedChatMessage current = messages.get(index);
            List<CachedChatMessage> turn = new ArrayList<>();
            turn.add(current);
            if ("user".equalsIgnoreCase(current.role())
                    && index + 1 < messages.size()
                    && "assistant".equalsIgnoreCase(messages.get(index + 1).role())) {
                turn.add(messages.get(index + 1));
                index += 2;
            } else {
                index++;
            }
            turns.add(List.copyOf(turn));
        }
        return turns;
    }

    private QueryRewriteResult fallbackResult(
            TerminologyNormalizationResult normalization,
            QueryPipelineConfigView config,
            String reason
    ) {
        if (FALLBACK_BYPASS.equalsIgnoreCase(config.fallbackPolicy())) {
            return QueryRewriteResult.fallback(normalization.originalQuery(), List.of(), reason);
        }
        if (FALLBACK_RULE_SPLIT.equalsIgnoreCase(config.fallbackPolicy()) && config.ruleSplitEnabled()) {
            return QueryRewriteResult.ruleSplit(
                    normalization.normalizedQuery(),
                    ruleBasedSplit(normalization.normalizedQuery()),
                    normalization.matches(),
                    reason
            );
        }
        if (config.ruleSplitEnabled() && FALLBACK_TERM_ONLY.equalsIgnoreCase(config.fallbackPolicy())) {
            return QueryRewriteResult.fallback(normalization.normalizedQuery(), normalization.matches(), reason);
        }
        return QueryRewriteResult.fallback(normalization.normalizedQuery(), normalization.matches(), reason);
    }

    private List<String> ruleBasedSplit(String question) {
        if (question == null || question.isBlank()) {
            return List.of("");
        }
        List<String> parts = Arrays.stream(question.split("[?？。；;\\n]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (parts.size() < 2) {
            return List.of(question);
        }
        return parts.stream().map(this::ensureQuestionMark).toList();
    }

    private String ensureQuestionMark(String text) {
        if (text.endsWith("?") || text.endsWith("？")) {
            return text;
        }
        return text + "？";
    }

    private void applyResult(ChatExecutionContext ctx, QueryRewriteResult result) {
        ctx.setRewriteResult(result);
        ctx.setRewrittenQuery(result.rewrite());
        ctx.setSubQueries(result.subQuestions());
    }

    private void finishRewrite(
            ChatExecutionContext ctx,
            QueryRewriteResult result,
            QueryPipelineConfigView config,
            String rawModelResponse,
            String errorMessage,
            long startedAt
    ) {
        long durationMs = elapsedMillis(startedAt);
        ctx.setQueryRewriteDurationMs(durationMs);
        applyResult(ctx, result);
        log.info(
                "event=query_rewrite_resolved conversationId={} userMessageId={} sourceType={} success={} fallbackReason={} subQuestionCount={} timeoutMs={} durationMs={}",
                ctx.conversation() == null ? "-" : ctx.conversation().getConversationNo(),
                ctx.userMessage() == null ? "-" : ctx.userMessage().getId(),
                result.sourceType(),
                result.success(),
                result.fallbackReason() == null ? "-" : result.fallbackReason(),
                result.subQuestions().size(),
                config.rewriteTimeoutMs(),
                durationMs
        );
        recordService.record(ctx, result, PROMPT_VERSION, rawModelResponse, errorMessage, durationMs);
    }

    private String fallbackReason(Exception exception) {
        String simpleName = exception.getClass().getSimpleName();
        if ("TimeoutException".equals(simpleName)) {
            return "LLM_TIMEOUT";
        }
        if (exception instanceof IllegalArgumentException) {
            return "JSON_PARSE_FAILED";
        }
        return "LLM_FAILED";
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
