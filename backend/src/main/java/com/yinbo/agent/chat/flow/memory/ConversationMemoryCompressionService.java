package com.yinbo.agent.chat.flow.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.dto.ConversationMemoryCompressionResponse;
import com.yinbo.agent.chat.entity.ChatConversation;
import com.yinbo.agent.chat.entity.ConversationMemorySummary;
import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.lifecycle.ConversationStreamRegistry;
import com.yinbo.agent.chat.mapper.ConversationMemorySummaryMapper;
import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.ChatMemoryProperties;
import com.yinbo.ai.api.chat.LLMMessage;
import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
// 会话记忆压缩服务，负责自动触发、手动触发和 Prompt 记忆视图构建。
public class ConversationMemoryCompressionService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryCompressionService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String TRIGGER_AUTO = "AUTO";
    private static final String TRIGGER_MANUAL = "MANUAL";

    private final ConversationMemorySummaryMapper memorySummaryMapper;
    private final LLMService llmService;
    private final ChatMemoryProperties memoryProperties;
    private final ConversationTokenEstimator tokenEstimator;
    private final ConversationStreamRegistry streamRegistry;
    private final ConcurrentMap<Long, ReentrantLock> compressionLocks = new ConcurrentHashMap<>();

    // 注入记忆摘要 Mapper、LLM 服务、压缩配置、token 估算器和流式状态登记器。
    public ConversationMemoryCompressionService(
            ConversationMemorySummaryMapper memorySummaryMapper,
            LLMService llmService,
            ChatMemoryProperties memoryProperties,
            ConversationTokenEstimator tokenEstimator,
            ConversationStreamRegistry streamRegistry
    ) {
        this.memorySummaryMapper = memorySummaryMapper;
        this.llmService = llmService;
        this.memoryProperties = memoryProperties;
        this.tokenEstimator = tokenEstimator;
        this.streamRegistry = streamRegistry;
    }

    // 根据当前预算决定是否自动压缩，并写入 Prompt 使用的记忆视图。
    public void preparePromptMemory(ChatExecutionContext ctx) {
        List<CachedChatMessage> sortedMessages = sortByMessageId(ctx.conversationMessages());
        ConversationMemorySummary activeSummary = selectActiveSummary(ctx.authUser().getId(), ctx.conversation().getId());
        ctx.setMemorySummary(activeSummary);

        int memoryTokens = estimatePromptMemoryTokens(activeSummary, sortedMessages);
        int thresholdTokens = memoryProperties.autoCompressThresholdTokens();
        if (memoryTokens >= thresholdTokens) {
            try {
                CompressionResult result = compressWithLock(
                        ctx.authUser().getId(),
                        ctx.conversation().getId(),
                        ctx.conversation().getConversationNo(),
                        ctx.model().id(),
                        activeSummary,
                        sortedMessages,
                        TRIGGER_AUTO,
                        false
                );
                if (result.compressed()) {
                    activeSummary = result.summary();
                }
            } catch (Exception exception) {
                log.warn(
                        "event=conversation_memory_auto_compress_failed userId={} conversationId={} modelId={} type={} message={}",
                        ctx.authUser().getId(),
                        ctx.conversation().getConversationNo(),
                        ctx.model().id(),
                        exception.getClass().getSimpleName(),
                        sanitizeLogValue(exception.getMessage()),
                        exception
                );
            }
        }

        ctx.setMemorySummary(activeSummary);
        ctx.setPromptConversationMessages(buildPromptMessages(activeSummary, sortedMessages));
    }

    // 手动压缩指定会话记忆。
    public ConversationMemoryCompressionResponse compressManually(
            AuthUser authUser,
            ChatConversation conversation,
            List<CachedChatMessage> conversationMessages
    ) {
        if (streamRegistry.isStreaming(conversation.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前会话正在流式输出，结束后再压缩");
        }

        List<CachedChatMessage> sortedMessages = sortByMessageId(conversationMessages);
        ConversationMemorySummary activeSummary = selectActiveSummary(authUser.getId(), conversation.getId());
        try {
            CompressionResult result = compressWithLock(
                    authUser.getId(),
                    conversation.getId(),
                    conversation.getConversationNo(),
                    conversation.getModelId(),
                    activeSummary,
                    sortedMessages,
                    TRIGGER_MANUAL,
                    true
            );
            return toResponse(conversation.getConversationNo(), result, TRIGGER_MANUAL);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn(
                    "event=conversation_memory_manual_compress_failed userId={} conversationId={} modelId={} type={} message={}",
                    authUser.getId(),
                    conversation.getConversationNo(),
                    conversation.getModelId(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "会话记忆压缩失败，请稍后重试");
        }
    }

    // 判断指定会话是否正在压缩记忆。
    public boolean isCompressing(Long conversationId) {
        ReentrantLock lock = conversationId == null ? null : compressionLocks.get(conversationId);
        return lock != null && lock.isLocked();
    }

    // 查询当前会话的活跃摘要。
    public ConversationMemorySummary selectActiveSummary(Long userId, Long conversationId) {
        return memorySummaryMapper.selectOne(new LambdaQueryWrapper<ConversationMemorySummary>()
                .eq(ConversationMemorySummary::getUserId, userId)
                .eq(ConversationMemorySummary::getConversationId, conversationId)
                .eq(ConversationMemorySummary::getStatus, STATUS_ACTIVE)
                .orderByDesc(ConversationMemorySummary::getId)
                .last("LIMIT 1"));
    }

    // 删除指定会话的所有摘要。
    public void deleteSummaries(Long userId, Long conversationId) {
        memorySummaryMapper.delete(new LambdaQueryWrapper<ConversationMemorySummary>()
                .eq(ConversationMemorySummary::getUserId, userId)
                .eq(ConversationMemorySummary::getConversationId, conversationId));
    }

    // 加会话级锁执行压缩，避免同一会话并发写入多个活跃摘要。
    private CompressionResult compressWithLock(
            Long userId,
            Long conversationId,
            String conversationNo,
            String modelId,
            ConversationMemorySummary activeSummary,
            List<CachedChatMessage> sortedMessages,
            String triggerType,
            boolean failIfLocked
    ) {
        ReentrantLock lock = compressionLocks.computeIfAbsent(conversationId, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            if (failIfLocked) {
                throw new BusinessException(HttpStatus.CONFLICT, "当前会话正在压缩，请稍后再试");
            }
            log.info(
                    "event=conversation_memory_compress_skipped_locked trigger={} userId={} conversationId={} modelId={}",
                    triggerType,
                    userId,
                    conversationNo,
                    normalizeModelId(modelId)
            );
            return CompressionResult.skipped(activeSummary, "当前会话正在压缩");
        }
        try {
            return compress(userId, conversationId, conversationNo, modelId, activeSummary, sortedMessages, triggerType);
        } finally {
            lock.unlock();
        }
    }

    // 执行一次压缩。
    private CompressionResult compress(
            Long userId,
            Long conversationId,
            String conversationNo,
            String modelId,
            ConversationMemorySummary activeSummary,
            List<CachedChatMessage> sortedMessages,
            String triggerType
    ) {
        CompressPlan plan = buildCompressPlan(activeSummary, sortedMessages);
        if (!plan.compressible()) {
            return CompressionResult.skipped(activeSummary, plan.skipMessage());
        }

        String summaryContent = summarizeWithWindows(modelId, activeSummary, plan.messages());
        if (summaryContent == null || summaryContent.isBlank()) {
            return CompressionResult.skipped(activeSummary, "压缩模型没有返回有效摘要");
        }

        ConversationMemorySummary nextSummary = new ConversationMemorySummary();
        nextSummary.setConversationId(conversationId);
        nextSummary.setUserId(userId);
        nextSummary.setSummaryContent(summaryContent);
        nextSummary.setCoveredStartMessageId(plan.coveredStartMessageId());
        nextSummary.setCoveredEndMessageId(plan.coveredEndMessageId());
        nextSummary.setSourceMessageCount(plan.sourceMessageCount());
        nextSummary.setSummaryTokens(tokenEstimator.estimateText(summaryContent));
        nextSummary.setCompressionModelId(normalizeModelId(modelId));
        nextSummary.setCompressionVersion(memoryProperties.compressionVersion());
        nextSummary.setTriggerType(triggerType);
        nextSummary.setStatus(STATUS_ACTIVE);
        memorySummaryMapper.insert(nextSummary);
        archivePreviousSummary(activeSummary);

        log.info(
                "event=conversation_memory_compressed trigger={} userId={} conversationId={} coveredStartMessageId={} coveredEndMessageId={} sourceMessageCount={} summaryTokens={} modelId={}",
                triggerType,
                userId,
                conversationNo,
                nextSummary.getCoveredStartMessageId(),
                nextSummary.getCoveredEndMessageId(),
                nextSummary.getSourceMessageCount(),
                nextSummary.getSummaryTokens(),
                nextSummary.getCompressionModelId()
        );
        return CompressionResult.compressed(nextSummary);
    }

    // 基于旧摘要和消息窗口滚动生成新摘要。
    private String summarizeWithWindows(
            String modelId,
            ConversationMemorySummary activeSummary,
            List<CachedChatMessage> messages
    ) {
        String rollingSummary = activeSummary == null ? "" : activeSummary.getSummaryContent();
        List<List<CachedChatMessage>> windows = splitWindows(messages);
        for (int index = 0; index < windows.size(); index++) {
            rollingSummary = summarizeWindow(modelId, rollingSummary, windows.get(index), index + 1, windows.size());
        }
        return rollingSummary == null ? "" : rollingSummary.trim();
    }

    // 调用 LLM 压缩一个消息窗口。
    private String summarizeWindow(
            String modelId,
            String existingSummary,
            List<CachedChatMessage> windowMessages,
            int windowIndex,
            int windowCount
    ) {
        List<LLMMessage> messages = List.of(
                new LLMMessage("system", compressionSystemPrompt()),
                new LLMMessage("user", compressionUserPrompt(existingSummary, windowMessages, windowIndex, windowCount))
        );
        LLMResponse response = llmService.chat(new LLMRequest(normalizeModelId(modelId), false, messages));
        String content = response == null ? null : response.content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("压缩模型返回空摘要");
        }
        return content.trim();
    }

    // 构造压缩模型系统提示词。
    private String compressionSystemPrompt() {
        return """
                你是会话记忆压缩器，只负责把历史对话压缩成后续可继续使用的结构化 summary。
                只输出 summary 正文，不要寒暄，不要解释压缩过程，不要使用 Markdown 代码块。
                必须保留：用户身份和偏好、项目事实、已经确认的技术方案、关键约束、未完成任务、重要文件路径、重要错误和结论。
                必须丢弃：问候、重复表达、已经被否定的方案、无长期价值的中间日志。
                如果已有 summary，需要把新消息合并进去，生成一个新的完整 summary。
                """;
    }

    // 构造压缩模型用户提示词。
    private String compressionUserPrompt(
            String existingSummary,
            List<CachedChatMessage> windowMessages,
            int windowIndex,
            int windowCount
    ) {
        String summaryText = existingSummary == null || existingSummary.isBlank() ? "暂无" : existingSummary;
        return """
                已有 summary：
                %s

                当前压缩窗口：%d / %d

                本窗口待压缩 message：
                %s

                请生成新的完整 summary，控制在约 %d tokens 内。
                """.formatted(
                summaryText,
                windowIndex,
                windowCount,
                formatMessages(windowMessages),
                memoryProperties.maxSummaryTokens()
        );
    }

    // 构造压缩范围。
    private CompressPlan buildCompressPlan(
            ConversationMemorySummary activeSummary,
            List<CachedChatMessage> sortedMessages
    ) {
        List<CachedChatMessage> messagesWithId = sortedMessages.stream()
                .filter(message -> message.id() != null)
                .toList();
        if (messagesWithId.isEmpty()) {
            return CompressPlan.skipped("当前会话暂无可压缩消息");
        }

        Long coveredEndMessageId = activeSummary == null ? null : activeSummary.getCoveredEndMessageId();
        RecentWindow recentWindow = buildRecentWindow(messagesWithId, coveredEndMessageId);
        if (recentWindow.startMessageId() == null) {
            return CompressPlan.skipped("当前会话暂无需要压缩的历史消息");
        }
        List<CachedChatMessage> compressMessages;
        Long coveredStartMessageId;
        if (coveredEndMessageId != null) {
            compressMessages = messagesWithId.stream()
                    .filter(message -> message.id() > coveredEndMessageId)
                    .filter(message -> message.id() < recentWindow.startMessageId())
                    .toList();
            if (compressMessages.isEmpty()) {
                return CompressPlan.skipped("当前会话暂无需要压缩的历史消息");
            }
            coveredStartMessageId = activeSummary.getCoveredStartMessageId() != null
                    ? activeSummary.getCoveredStartMessageId()
                    : compressMessages.get(0).id();
        } else {
            int startIndex = Math.min(memoryProperties.headMessageCount(), messagesWithId.size());
            int endExclusive = indexOfMessageId(messagesWithId, recentWindow.startMessageId());
            if (startIndex >= endExclusive) {
                return CompressPlan.skipped("当前会话暂无需要压缩的历史消息");
            }
            compressMessages = List.copyOf(messagesWithId.subList(startIndex, endExclusive));
            coveredStartMessageId = compressMessages.get(0).id();
        }

        if (compressMessages.size() < memoryProperties.minCompressMessageCount()) {
            return CompressPlan.skipped("当前会话暂无足够多的可压缩历史消息");
        }

        Long newCoveredEndMessageId = compressMessages.get(compressMessages.size() - 1).id();
        int previousSourceCount = activeSummary == null || activeSummary.getSourceMessageCount() == null
                ? 0
                : activeSummary.getSourceMessageCount();
        return CompressPlan.ready(
                coveredStartMessageId,
                newCoveredEndMessageId,
                previousSourceCount + compressMessages.size(),
                compressMessages
        );
    }

    // 从尾部按 token 反向收集最近窗口，并尽量保持完整轮次。
    private RecentWindow buildRecentWindow(List<CachedChatMessage> messagesWithId, Long coveredEndMessageId) {
        List<CachedChatMessage> tailMessages = messagesWithId.stream()
                .filter(message -> coveredEndMessageId == null || message.id() > coveredEndMessageId)
                .toList();
        List<MessageTurn> turns = buildConversationTurns(tailMessages);
        if (turns.isEmpty()) {
            return RecentWindow.empty();
        }

        List<CachedChatMessage> recentMessages = new ArrayList<>();
        int recentTokens = 0;
        int recentTokenBudget = memoryProperties.recentWindowTokens();
        for (int index = turns.size() - 1; index >= 0; index--) {
            MessageTurn turn = turns.get(index);
            if (!recentMessages.isEmpty() && recentTokens + turn.tokens() > recentTokenBudget) {
                break;
            }
            recentMessages.addAll(0, turn.messages());
            recentTokens += turn.tokens();
        }

        if (recentMessages.isEmpty()) {
            MessageTurn lastTurn = turns.get(turns.size() - 1);
            recentMessages.addAll(lastTurn.messages());
            recentTokens = lastTurn.tokens();
        }

        return new RecentWindow(recentMessages.get(0).id(), recentTokens, List.copyOf(recentMessages));
    }

    // 将消息按压缩窗口 token 预算切分，并尽量保持完整轮次。
    private List<List<CachedChatMessage>> splitWindows(List<CachedChatMessage> messages) {
        List<List<CachedChatMessage>> windows = new ArrayList<>();
        List<CachedChatMessage> currentWindow = new ArrayList<>();
        int currentTokens = 0;
        int windowBudget = memoryProperties.compressionWindowTokens();
        for (MessageTurn turn : buildConversationTurns(messages)) {
            if (!currentWindow.isEmpty() && currentTokens + turn.tokens() > windowBudget) {
                windows.add(List.copyOf(currentWindow));
                currentWindow.clear();
                currentTokens = 0;
            }
            currentWindow.addAll(turn.messages());
            currentTokens += turn.tokens();
        }
        if (!currentWindow.isEmpty()) {
            windows.add(List.copyOf(currentWindow));
        }
        return windows;
    }

    // 构造发给压缩模型的消息文本。
    private String formatMessages(List<CachedChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (CachedChatMessage message : messages) {
            builder.append("messageId: ").append(message.id()).append('\n');
            builder.append("role: ").append(message.role()).append('\n');
            builder.append("createdAt: ").append(message.createdAt()).append('\n');
            builder.append("content:\n").append(message.content()).append("\n\n---\n");
        }
        return builder.toString();
    }

    // 估算当前 Prompt 记忆视图 token 数。
    private int estimatePromptMemoryTokens(ConversationMemorySummary summary, List<CachedChatMessage> sortedMessages) {
        return tokenEstimator.estimateMessages(buildPromptMessages(summary, sortedMessages));
    }

    // 根据摘要水位线构造 Prompt 记忆视图。
    private List<CachedChatMessage> buildPromptMessages(
            ConversationMemorySummary summary,
            List<CachedChatMessage> sortedMessages
    ) {
        if (summary == null) {
            return List.copyOf(sortedMessages);
        }
        Long coveredStartMessageId = summary.getCoveredStartMessageId();
        Long coveredEndMessageId = summary.getCoveredEndMessageId();
        List<CachedChatMessage> promptMessages = new ArrayList<>();
        for (CachedChatMessage message : sortedMessages) {
            if (message.id() != null && coveredStartMessageId != null && message.id() < coveredStartMessageId) {
                promptMessages.add(message);
            }
        }
        promptMessages.add(toSummaryPromptMessage(summary));
        for (CachedChatMessage message : sortedMessages) {
            if (message.id() == null || coveredEndMessageId == null || message.id() > coveredEndMessageId) {
                promptMessages.add(message);
            }
        }
        return List.copyOf(promptMessages);
    }

    // 把摘要转换为 Prompt 中的系统消息。
    private CachedChatMessage toSummaryPromptMessage(ConversationMemorySummary summary) {
        String content = """
                【历史会话摘要】
                %s

                摘要覆盖到 messageId = %d。
                ----- 上下文已压缩，以下为最近未压缩对话 -----
                """.formatted(summary.getSummaryContent(), summary.getCoveredEndMessageId());
        return new CachedChatMessage(
                null,
                "system",
                content,
                summary.getCompressionModelId(),
                toInstant(summary.getCreatedAt()),
                null,
                summary.getSummaryTokens()
        );
    }

    // 归档旧的活跃摘要。
    private void archivePreviousSummary(ConversationMemorySummary previousSummary) {
        if (previousSummary == null || previousSummary.getId() == null) {
            return;
        }
        memorySummaryMapper.update(null, new LambdaUpdateWrapper<ConversationMemorySummary>()
                .eq(ConversationMemorySummary::getId, previousSummary.getId())
                .set(ConversationMemorySummary::getStatus, STATUS_ARCHIVED)
                .set(ConversationMemorySummary::getUpdatedAt, LocalDateTime.now()));
    }

    // 转换成手动压缩接口响应。
    private ConversationMemoryCompressionResponse toResponse(
            String conversationNo,
            CompressionResult result,
            String triggerType
    ) {
        ConversationMemorySummary summary = result.summary();
        if (summary == null) {
            return new ConversationMemoryCompressionResponse(
                    conversationNo,
                    false,
                    triggerType,
                    null,
                    null,
                    0,
                    0,
                    Instant.now(),
                    result.message()
            );
        }
        if (!result.compressed()) {
            return new ConversationMemoryCompressionResponse(
                    conversationNo,
                    false,
                    triggerType,
                    summary.getCoveredStartMessageId(),
                    summary.getCoveredEndMessageId(),
                    summary.getSourceMessageCount(),
                    summary.getSummaryTokens(),
                    toInstant(summary.getCreatedAt()),
                    result.message()
            );
        }
        return new ConversationMemoryCompressionResponse(
                conversationNo,
                true,
                triggerType,
                summary.getCoveredStartMessageId(),
                summary.getCoveredEndMessageId(),
                summary.getSourceMessageCount(),
                summary.getSummaryTokens(),
                toInstant(summary.getCreatedAt()),
                "会话记忆已压缩"
        );
    }

    // 按消息 ID 排序，缺少 ID 的消息放到最后兜底保留。
    private List<CachedChatMessage> sortByMessageId(List<CachedChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .sorted(Comparator.comparing(CachedChatMessage::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    // 规范化模型 ID。
    private String normalizeModelId(String modelId) {
        return modelId == null || modelId.isBlank() ? "default" : modelId.trim();
    }

    // 转换为 Instant。
    private Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    // 清洗日志字段值。
    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    // 按完整轮次构造消息分组，默认按 user + assistant 配对。
    private List<MessageTurn> buildConversationTurns(List<CachedChatMessage> messages) {
        List<MessageTurn> turns = new ArrayList<>();
        int index = 0;
        while (index < messages.size()) {
            CachedChatMessage current = messages.get(index);
            List<CachedChatMessage> turnMessages = new ArrayList<>();
            turnMessages.add(current);
            int turnTokens = tokenEstimator.estimateMessage(current);

            if (isUserMessage(current)
                    && index + 1 < messages.size()
                    && isAssistantMessage(messages.get(index + 1))) {
                CachedChatMessage assistantMessage = messages.get(index + 1);
                turnMessages.add(assistantMessage);
                turnTokens += tokenEstimator.estimateMessage(assistantMessage);
                index += 2;
            } else {
                index++;
            }

            turns.add(new MessageTurn(List.copyOf(turnMessages), turnTokens));
        }
        return turns;
    }

    // 判断消息是否为用户消息。
    private boolean isUserMessage(CachedChatMessage message) {
        return message != null && "user".equalsIgnoreCase(message.role());
    }

    // 判断消息是否为 assistant 消息。
    private boolean isAssistantMessage(CachedChatMessage message) {
        return message != null && "assistant".equalsIgnoreCase(message.role());
    }

    // 根据消息 ID 找到最近窗口的起始索引。
    private int indexOfMessageId(List<CachedChatMessage> messages, Long messageId) {
        if (messageId == null) {
            return messages.size();
        }
        for (int index = 0; index < messages.size(); index++) {
            if (messageId <= messages.get(index).id()) {
                return index;
            }
        }
        return messages.size();
    }

    // 压缩范围计划。
    private record CompressPlan(
            boolean compressible,
            Long coveredStartMessageId,
            Long coveredEndMessageId,
            int sourceMessageCount,
            List<CachedChatMessage> messages,
            String skipMessage
    ) {

        // 构造可执行压缩计划。
        private static CompressPlan ready(
                Long coveredStartMessageId,
                Long coveredEndMessageId,
                int sourceMessageCount,
                List<CachedChatMessage> messages
        ) {
            return new CompressPlan(
                    true,
                    coveredStartMessageId,
                    coveredEndMessageId,
                    sourceMessageCount,
                    List.copyOf(messages),
                    null
            );
        }

        // 构造跳过压缩计划。
        private static CompressPlan skipped(String message) {
            return new CompressPlan(false, null, null, 0, List.of(), message);
        }
    }

    // 最近窗口信息，记录起始 messageId 和 token 总量。
    private record RecentWindow(Long startMessageId, int tokens, List<CachedChatMessage> messages) {

        private static RecentWindow empty() {
            return new RecentWindow(null, 0, List.of());
        }
    }

    // 会话轮次信息，尽量保持 user / assistant 成对。
    private record MessageTurn(List<CachedChatMessage> messages, int tokens) {
    }

    // 压缩执行结果。
    private record CompressionResult(boolean compressed, ConversationMemorySummary summary, String message) {

        // 构造压缩成功结果。
        private static CompressionResult compressed(ConversationMemorySummary summary) {
            return new CompressionResult(true, summary, "会话记忆已压缩");
        }

        // 构造跳过压缩结果。
        private static CompressionResult skipped(ConversationMemorySummary summary, String message) {
            return new CompressionResult(false, summary, message);
        }
    }
}
