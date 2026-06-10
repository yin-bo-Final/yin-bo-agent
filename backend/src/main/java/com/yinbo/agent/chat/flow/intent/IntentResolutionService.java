package com.yinbo.agent.chat.flow.intent;

import com.yinbo.agent.chat.flow.context.ChatExecutionContext;
import com.yinbo.agent.chat.flow.context.ChatIntentType;
import com.yinbo.agent.chat.flow.intent.RuleIntentRouter.RuleRouteResult;
import com.yinbo.agent.chat.flow.intent.model.IntentKind;
import com.yinbo.agent.chat.flow.intent.model.IntentNode;
import com.yinbo.agent.chat.flow.intent.model.IntentResolveResult;
import com.yinbo.agent.chat.flow.intent.model.IntentTreeData;
import com.yinbo.agent.chat.flow.intent.model.NodeScore;
import com.yinbo.agent.chat.flow.intent.model.SubQuestionIntent;
import com.yinbo.agent.config.ChatIntentProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
// 会话意图识别服务：规则优先、LLM 兜底、总量封顶和歧义引导。
public class IntentResolutionService {

    private static final Logger log = LoggerFactory.getLogger(IntentResolutionService.class);

    private final IntentTreeService intentTreeService;
    private final RuleIntentRouter ruleIntentRouter;
    private final IntentClassifier intentClassifier;
    private final ChatIntentProperties properties;
    private final ExecutorService intentClassifyExecutor;

    // 注入意图树、规则路由、LLM 分类器和配置。
    public IntentResolutionService(
            IntentTreeService intentTreeService,
            RuleIntentRouter ruleIntentRouter,
            IntentClassifier intentClassifier,
            ChatIntentProperties properties,
            @Qualifier("intentClassifyExecutor") ExecutorService intentClassifyExecutor
    ) {
        this.intentTreeService = intentTreeService;
        this.ruleIntentRouter = ruleIntentRouter;
        this.intentClassifier = intentClassifier;
        this.properties = properties;
        this.intentClassifyExecutor = intentClassifyExecutor;
    }

    // 识别用户意图并写回会话上下文。
    public void resolve(ChatExecutionContext ctx) {
        if (!properties.enabled()) {
            applyResult(ctx, IntentResolveResult.empty());
            return;
        }

        IntentTreeData treeData = intentTreeService.loadEnabledTreeData();
        if (treeData.leafNodes().isEmpty()) {
            applyResult(ctx, IntentResolveResult.empty());
            return;
        }

        List<String> questions = resolveQuestions(ctx);
        List<SubQuestionIntent> subQuestionIntents = classifyQuestions(ctx, questions, treeData);
        List<SubQuestionIntent> capped = capTotalIntents(subQuestionIntents);
        IntentResolveResult result = buildResolveResult(capped);
        applyResult(ctx, result);
    }

    // 判断当前会话是否可以直接调用 LLM。
    public boolean isDirectChat(ChatExecutionContext ctx) {
        return ctx.hasIntent(ChatIntentType.DIRECT_CHAT);
    }

    private List<SubQuestionIntent> classifyQuestions(
            ChatExecutionContext ctx,
            List<String> questions,
            IntentTreeData treeData
    ) {
        List<CompletableFuture<SubQuestionIntent>> tasks = questions.stream()
                .map(question -> CompletableFuture.supplyAsync(
                        () -> classifyQuestionSafely(ctx, question, treeData),
                        intentClassifyExecutor
                ))
                .toList();
        CompletableFuture<Void> all = CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
        try {
            all.get(properties.classifyTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancelUnfinished(tasks);
            log.warn(
                    "event=intent_subquestion_classification_interrupted conversationId={} timeoutMs={}",
                    conversationId(ctx),
                    properties.classifyTimeoutMs()
            );
        } catch (TimeoutException exception) {
            cancelUnfinished(tasks);
            log.warn(
                    "event=intent_subquestion_classification_timeout conversationId={} timeoutMs={} type={} message={}",
                    conversationId(ctx),
                    properties.classifyTimeoutMs(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
        } catch (ExecutionException exception) {
            cancelUnfinished(tasks);
            log.warn(
                    "event=intent_subquestion_classification_failed conversationId={} type={} message={}",
                    conversationId(ctx),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
        }

        List<SubQuestionIntent> result = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            CompletableFuture<SubQuestionIntent> task = tasks.get(index);
            if (!task.isDone() || task.isCompletedExceptionally() || task.isCancelled()) {
                result.add(new SubQuestionIntent(questions.get(index), List.of()));
                continue;
            }
            result.add(task.getNow(new SubQuestionIntent(questions.get(index), List.of())));
        }
        return result;
    }

    private SubQuestionIntent classifyQuestionSafely(
            ChatExecutionContext ctx,
            String question,
            IntentTreeData treeData
    ) {
        try {
            return new SubQuestionIntent(question, classifyQuestion(ctx, question, treeData));
        } catch (Exception exception) {
            log.warn(
                    "event=intent_subquestion_classification_failed conversationId={} question={} type={} message={}",
                    conversationId(ctx),
                    sanitizeLogValue(question),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
            return new SubQuestionIntent(question, List.of());
        }
    }

    private void cancelUnfinished(List<CompletableFuture<SubQuestionIntent>> tasks) {
        for (CompletableFuture<SubQuestionIntent> task : tasks) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }
    }

    private List<NodeScore> classifyQuestion(ChatExecutionContext ctx, String question, IntentTreeData treeData) {
        RuleRouteResult ruleResult = ruleIntentRouter.route(question, treeData);
        if (!ruleResult.strongScores().isEmpty()) {
            return ruleResult.strongScores().stream()
                    .limit(properties.maxIntents())
                    .toList();
        }

        List<IntentNode> candidates = ruleResult.candidateLeaves().isEmpty()
                ? treeData.leafNodes()
                : ruleResult.candidateLeaves();
        return intentClassifier.classify(ctx, question, candidates).stream()
                .filter(this::aboveMinScore)
                .limit(properties.maxIntents())
                .toList();
    }

    private boolean aboveMinScore(NodeScore nodeScore) {
        double threshold = nodeScore.node().getMinScore() == null
                ? properties.minScore()
                : nodeScore.node().getMinScore();
        return nodeScore.score() >= threshold;
    }

    private List<String> resolveQuestions(ChatExecutionContext ctx) {
        List<String> questions = ctx.subQueries() == null ? List.of() : ctx.subQueries().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (!questions.isEmpty()) {
            return questions;
        }
        if (ctx.rewrittenQuery() != null && !ctx.rewrittenQuery().isBlank()) {
            return List.of(ctx.rewrittenQuery().trim());
        }
        if (ctx.originalQuery() != null && !ctx.originalQuery().isBlank()) {
            return List.of(ctx.originalQuery().trim());
        }
        return List.of("");
    }

    private List<SubQuestionIntent> capTotalIntents(List<SubQuestionIntent> subQuestionIntents) {
        int total = subQuestionIntents.stream()
                .mapToInt(item -> item.nodeScores().size())
                .sum();
        if (total <= properties.maxIntents()) {
            return subQuestionIntents;
        }

        List<IntentCandidate> allCandidates = collectAllCandidates(subQuestionIntents);
        List<IntentCandidate> guaranteed = selectTopIntentPerSubQuestion(allCandidates);
        if (guaranteed.size() >= properties.maxIntents()) {
            return rebuildSubIntents(subQuestionIntents, guaranteed.stream()
                    .sorted(Comparator.comparingDouble((IntentCandidate candidate) -> candidate.nodeScore().score()).reversed())
                    .limit(properties.maxIntents())
                    .toList());
        }

        int remaining = properties.maxIntents() - guaranteed.size();
        List<IntentCandidate> selected = new ArrayList<>(guaranteed);
        for (IntentCandidate candidate : allCandidates) {
            if (selected.contains(candidate)) {
                continue;
            }
            selected.add(candidate);
            if (selected.size() >= guaranteed.size() + remaining) {
                break;
            }
        }
        return rebuildSubIntents(subQuestionIntents, selected);
    }

    private List<IntentCandidate> collectAllCandidates(List<SubQuestionIntent> subQuestionIntents) {
        List<IntentCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < subQuestionIntents.size(); index++) {
            for (NodeScore score : subQuestionIntents.get(index).nodeScores()) {
                candidates.add(new IntentCandidate(index, score));
            }
        }
        candidates.sort(Comparator.comparingDouble((IntentCandidate candidate) -> candidate.nodeScore().score()).reversed());
        return candidates;
    }

    private List<IntentCandidate> selectTopIntentPerSubQuestion(List<IntentCandidate> allCandidates) {
        Set<Integer> selectedIndexes = new LinkedHashSet<>();
        List<IntentCandidate> result = new ArrayList<>();
        for (IntentCandidate candidate : allCandidates) {
            if (selectedIndexes.add(candidate.subQuestionIndex())) {
                result.add(candidate);
            }
        }
        return result;
    }

    private List<SubQuestionIntent> rebuildSubIntents(
            List<SubQuestionIntent> original,
            List<IntentCandidate> selected
    ) {
        List<SubQuestionIntent> result = new ArrayList<>();
        for (int index = 0; index < original.size(); index++) {
            int currentIndex = index;
            List<NodeScore> retained = selected.stream()
                    .filter(candidate -> candidate.subQuestionIndex() == currentIndex)
                    .map(IntentCandidate::nodeScore)
                    .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                    .toList();
            result.add(new SubQuestionIntent(original.get(index).subQuestion(), retained));
        }
        return result;
    }

    private IntentResolveResult buildResolveResult(List<SubQuestionIntent> subQuestionIntents) {
        AmbiguityResult ambiguity = detectAmbiguity(subQuestionIntents);
        List<NodeScore> selected = subQuestionIntents.stream()
                .flatMap(item -> item.nodeScores().stream())
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .toList();
        return new IntentResolveResult(subQuestionIntents, selected, ambiguity.ambiguous(), ambiguity.guidanceQuestion());
    }

    private AmbiguityResult detectAmbiguity(List<SubQuestionIntent> subQuestionIntents) {
        for (SubQuestionIntent item : subQuestionIntents) {
            List<NodeScore> scores = item.nodeScores().stream()
                    .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                    .toList();
            if (scores.size() < 2) {
                continue;
            }
            NodeScore top = scores.get(0);
            NodeScore second = scores.get(1);
            if (top.score() >= properties.ambiguityMinScore()
                    && second.score() >= properties.ambiguityMinScore()
                    && top.score() - second.score() <= properties.ambiguityScoreGap()) {
                return new AmbiguityResult(true, guidanceQuestion(item.subQuestion(), top, second));
            }
        }
        return new AmbiguityResult(false, null);
    }

    private String guidanceQuestion(String question, NodeScore first, NodeScore second) {
        return """
                你这个问题有两种可能的方向：

                1. %s
                2. %s

                你想问哪一个？可以直接回复序号或补一句具体场景。
                """.formatted(displayPath(first), displayPath(second)).trim();
    }

    private String displayPath(NodeScore score) {
        String path = score.node().getFullPath();
        return path == null || path.isBlank() ? score.node().getName() : path;
    }

    private void applyResult(ChatExecutionContext ctx, IntentResolveResult result) {
        ctx.setIntentResult(result);
        ctx.setAmbiguous(result.ambiguous());
        ctx.setGuidanceQuestion(result.guidanceQuestion());
        ctx.setIntents(toChatIntentTypes(result));
    }

    private List<ChatIntentType> toChatIntentTypes(IntentResolveResult result) {
        if (result.ambiguous()) {
            return List.of(ChatIntentType.CLARIFICATION);
        }
        List<NodeScore> selected = result.selectedNodeScores();
        if (selected.isEmpty() || selected.stream().allMatch(score -> score.node().getKind() == IntentKind.SYSTEM)) {
            return List.of(ChatIntentType.DIRECT_CHAT);
        }
        boolean hasKB = selected.stream().anyMatch(score -> score.node().getKind() == IntentKind.KB);
        boolean hasMCP = selected.stream().anyMatch(score -> score.node().getKind() == IntentKind.MCP);
        if (hasKB && hasMCP) {
            return List.of(ChatIntentType.RAG_AND_TOOL);
        }
        if (hasKB) {
            return List.of(ChatIntentType.KNOWLEDGE_RAG);
        }
        if (hasMCP) {
            return List.of(ChatIntentType.TOOL_CALL);
        }
        return List.of(ChatIntentType.DIRECT_CHAT);
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

    private record IntentCandidate(int subQuestionIndex, NodeScore nodeScore) {
    }

    private record AmbiguityResult(boolean ambiguous, String guidanceQuestion) {
    }
}
