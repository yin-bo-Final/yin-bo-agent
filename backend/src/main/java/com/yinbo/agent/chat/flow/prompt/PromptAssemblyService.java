package com.yinbo.agent.chat.flow.prompt;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.query.QueryRewriteResult;
import com.yinbo.agent.chat.flow.retrieval.RetrievalContext;
import com.yinbo.agent.chat.service.ChatMessageCacheService.CachedChatMessage;
import com.yinbo.ai.api.chat.LLMMessage;
import com.yinbo.ai.api.chat.LLMRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
// 会话 Prompt 组装服务。
public class PromptAssemblyService {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是“音波AI agent 智能助手平台”的智能助手。
            你的目标是帮助用户完成学习、编程、资料整理和任务规划。
            回答要清晰、直接、可执行。
            当用户正在学习技术时，先解决问题，再用简洁语言解释背后的知识点。
            """;

    private static final String THINK_MODE_PROMPT = """

            当前启用了 Think 模式。
            你必须先输出一个可公开展示的“思考过程”摘要，再输出最终回答。
            注意：这里的“思考过程”是面向用户的推理摘要，不是完整隐藏推理链；不要使用 <think> 标签。
            必须严格使用下面的 Markdown 格式：

            **思考过程**
            - 用 2 到 5 条说明你如何拆解问题、判断关键点、选择方案。
            - 如果问题很简单，也至少给出 1 条简短判断。

            **最终回答**
            - 给出可执行、清晰的最终答案。
            """;

    private static final String NON_THINK_MODE_PROMPT = """

            当前未启用 Think 模式。
            你必须直接输出最终回答，不要输出“思考过程”“思考摘要”“最终回答”等标题。
            可以简洁解释原因和步骤，但不要把回答拆成思考过程和最终回答两个区域。
            """;

    private static final Pattern FINAL_ANSWER_HEADING_PATTERN = Pattern.compile(
            "(?s)(?:^|\\R|\\s)(?:(?:\\*\\*)?最终回答(?:\\*\\*)?|(?:\\*\\*回答\\*\\*)|回答\\s*[:：])\\s*[:：]?\\s*"
    );

    // 构造普通直聊请求。
    public LLMRequest buildDirectRequest(ChatExecutionContext ctx) {
        return buildRequest(promptMessagesWithRewrittenCurrentQuery(ctx), ctx.model().id(), ctx.request().thinkModeEnabled());
    }

    // 构造带检索上下文的请求，当前先复用普通直聊请求。
    public LLMRequest buildGroundedRequest(ChatExecutionContext ctx, RetrievalContext retrievalContext) {
        return buildDirectRequest(ctx);
    }

    // 构造发给模型的请求。
    private LLMRequest buildRequest(List<CachedChatMessage> conversationMessages, String modelId, boolean thinkMode) {
        List<LLMMessage> promptMessages = new ArrayList<>();
        promptMessages.add(new LLMMessage("system", systemPrompt(thinkMode)));

        conversationMessages.stream()
                .map(message -> toLlmMessage(message, thinkMode))
                .forEach(promptMessages::add);

        return new LLMRequest(modelId, thinkMode, promptMessages);
    }

    // 使用查询改写结果替换本轮用户消息的 Prompt 视图，消息落库仍保留用户原文。
    private List<CachedChatMessage> promptMessagesWithRewrittenCurrentQuery(ChatExecutionContext ctx) {
        String currentPromptContent = rewrittenCurrentPromptContent(ctx);
        if (currentPromptContent == null) {
            return ctx.promptConversationMessages();
        }

        Long currentUserMessageId = ctx.userMessage() == null ? null : ctx.userMessage().getId();
        List<CachedChatMessage> messages = ctx.promptConversationMessages();
        List<CachedChatMessage> rewrittenMessages = new ArrayList<>(messages.size());
        boolean replaced = false;
        for (CachedChatMessage message : messages) {
            if (!replaced && isCurrentUserMessage(message, currentUserMessageId)) {
                rewrittenMessages.add(withContent(message, currentPromptContent));
                replaced = true;
            } else {
                rewrittenMessages.add(message);
            }
        }
        if (replaced) {
            return rewrittenMessages;
        }
        return replaceLastUserMessage(messages, currentPromptContent);
    }

    // 构造本轮用户消息在 Prompt 中使用的改写文本。
    private String rewrittenCurrentPromptContent(ChatExecutionContext ctx) {
        QueryRewriteResult rewriteResult = ctx.rewriteResult();
        if (rewriteResult == null || rewriteResult.rewrite() == null || rewriteResult.rewrite().isBlank()) {
            return null;
        }
        String rewrite = rewriteResult.rewrite().trim();
        boolean queryChanged = ctx.originalQuery() == null || !rewrite.equals(ctx.originalQuery().trim());
        boolean split = rewriteResult.shouldSplit() && rewriteResult.subQuestions().size() > 1;
        if (!queryChanged && !split) {
            return null;
        }
        if (!split) {
            return rewrite;
        }
        return """
                用户当前问题（已做术语统一和语义改写，请以此为准回答）：
                %s

                拆分后的子问题：
                %s
                """.formatted(rewrite, formatSubQuestions(rewriteResult.subQuestions())).trim();
    }

    // 判断是否为本轮刚保存的用户消息。
    private boolean isCurrentUserMessage(CachedChatMessage message, Long currentUserMessageId) {
        return currentUserMessageId != null
                && message != null
                && currentUserMessageId.equals(message.id())
                && "user".equalsIgnoreCase(message.role());
    }

    // 兜底替换最后一条用户消息，避免缺少消息 ID 时改写结果完全失效。
    private List<CachedChatMessage> replaceLastUserMessage(List<CachedChatMessage> messages, String content) {
        List<CachedChatMessage> rewrittenMessages = new ArrayList<>(messages);
        for (int index = rewrittenMessages.size() - 1; index >= 0; index--) {
            CachedChatMessage message = rewrittenMessages.get(index);
            if (message != null && "user".equalsIgnoreCase(message.role())) {
                rewrittenMessages.set(index, withContent(message, content));
                return rewrittenMessages;
            }
        }
        return messages;
    }

    // 复制缓存消息并替换内容。
    private CachedChatMessage withContent(CachedChatMessage message, String content) {
        return new CachedChatMessage(
                message.id(),
                message.role(),
                content,
                message.modelId(),
                message.createdAt(),
                message.responseDurationMs(),
                message.totalTokens(),
                message.assistantTraceJson()
        );
    }

    // 格式化拆分后的子问题。
    private String formatSubQuestions(List<String> subQuestions) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < subQuestions.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(index + 1).append(". ").append(subQuestions.get(index));
        }
        return builder.toString();
    }

    // 根据模式构造系统提示词。
    private String systemPrompt(boolean thinkMode) {
        return thinkMode ? DEFAULT_SYSTEM_PROMPT + THINK_MODE_PROMPT : DEFAULT_SYSTEM_PROMPT + NON_THINK_MODE_PROMPT;
    }

    // 转换成模型消息。
    private LLMMessage toLlmMessage(CachedChatMessage message, boolean thinkMode) {
        String content = !thinkMode && "assistant".equalsIgnoreCase(message.role())
                ? finalAnswerOnly(message.content())
                : message.content();
        return new LLMMessage(normalizeRole(message.role()), content);
    }

    // 规范化消息角色。
    private String normalizeRole(String role) {
        return switch (role.toLowerCase()) {
            case "assistant", "system", "user", "tool" -> role.toLowerCase();
            default -> "user";
        };
    }

    // 非 Think 模式下只取历史 assistant 的最终回答部分。
    private String finalAnswerOnly(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        Matcher answerMatcher = FINAL_ANSWER_HEADING_PATTERN.matcher(content);
        if (!answerMatcher.find()) {
            return content;
        }
        String answer = content.substring(answerMatcher.end()).trim();
        return answer.isBlank() ? content : answer;
    }
}
