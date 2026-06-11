<script setup>
import { computed } from 'vue';

const props = defineProps({
  trace: {
    type: Object,
    required: true
  },
  messageId: {
    type: [String, Number],
    default: 'assistant-trace'
  }
});

const summaryItems = computed(() => assistantTraceSummary(props.trace));
const durationItems = computed(() => traceDurationItems(props.trace));
const durationTotalText = computed(() => traceDurationTotalText(props.trace, durationItems.value));
const subQuestions = computed(() => traceSubQuestions(props.trace));
const matchedTerms = computed(() => traceMatchedTerms(props.trace));
const intentLabels = computed(() => traceIntentLabels(props.trace));
const selectedNodes = computed(() => traceSelectedNodes(props.trace));
const ragText = computed(() => traceRagText(props.trace));

function formatTraceDuration(value) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const duration = Number(value);
  if (!Number.isFinite(duration)) {
    return '-';
  }
  if (duration >= 1000) {
    const seconds = duration / 1000;
    return `${seconds >= 10 ? seconds.toFixed(0) : seconds.toFixed(1)}s`;
  }
  return `${Math.max(0, Math.round(duration))}ms`;
}

function formatTraceBoolean(value) {
  return value ? '是' : '否';
}

function traceText(value, fallback = '-') {
  if (value === null || value === undefined || value === '') {
    return fallback;
  }
  return String(value);
}

function assistantTraceSummary(trace) {
  if (!trace) {
    return [];
  }
  return [
    ['模型', traceText(trace.modelId)],
    ['RAG', formatTraceBoolean(trace.enteredRag)],
    ['Fallback', traceText(trace.fallbackReason, '无')]
  ];
}

function traceDurationItems(trace) {
  if (!Array.isArray(trace?.durationStages)) {
    return [];
  }
  return trace.durationStages.map((stage) => ({
    code: stage.code,
    label: stage.label,
    durationMs: stage.durationMs,
    value: formatTraceDuration(stage.durationMs)
  }));
}

function traceDurationTotalText(trace, items) {
  const hasMissingDuration = items.some((item) => item.durationMs === null || item.durationMs === undefined || item.durationMs === '');
  if (!items.length || hasMissingDuration) {
    return `总耗时 ${formatTraceDuration(trace?.responseDurationMs)}`;
  }
  const stageTotal = items.reduce((sum, item) => sum + Math.max(0, Number(item.durationMs) || 0), 0);
  return `合计 ${formatTraceDuration(stageTotal)} / 总耗时 ${formatTraceDuration(trace.responseDurationMs)}`;
}

function traceSubQuestions(trace) {
  const questions = trace?.queryRewrite?.subQuestions;
  return Array.isArray(questions) ? questions.filter(Boolean) : [];
}

function traceMatchedTerms(trace) {
  const terms = trace?.queryRewrite?.matchedTerms;
  return Array.isArray(terms) ? terms.filter(Boolean) : [];
}

function traceIntentLabels(trace) {
  const intents = trace?.intentResolve?.intents;
  return Array.isArray(intents) && intents.length ? intents : ['-'];
}

function traceSelectedNodes(trace) {
  const nodes = trace?.intentResolve?.selectedNodes;
  return Array.isArray(nodes) ? nodes.filter(Boolean) : [];
}

function traceNodeTitle(node) {
  return traceText(node?.path || node?.nodeCode || node?.kind);
}

function traceNodeMeta(node) {
  const parts = [
    node?.kind,
    node?.source,
    Number.isFinite(Number(node?.score)) ? Number(node.score).toFixed(3) : null
  ].filter(Boolean);
  return parts.length ? parts.join(' / ') : '-';
}

function traceRagText(trace) {
  const rag = trace?.rag;
  if (!rag) {
    return trace?.enteredRag ? '已进入 RAG' : '未进入 RAG';
  }
  return `知识片段 ${Number(rag.knowledgeSnippetCount || 0)} · 工具结果 ${Number(rag.toolResultCount || 0)}`;
}

function traceSuccessText(value) {
  if (value === true) {
    return '成功';
  }
  if (value === false) {
    return '降级';
  }
  return '-';
}
</script>

<template>
  <section class="assistant-trace-panel" aria-label="本轮链路追踪">
    <header class="assistant-trace-header">
      <strong>本轮链路</strong>
      <span>{{ traceText(trace.fallbackReason, '无 fallback') }}</span>
    </header>

    <div class="assistant-trace-summary">
      <span
        v-for="(item, traceIndex) in summaryItems"
        :key="`${messageId}-trace-summary-${traceIndex}`"
      >
        <small>{{ item[0] }}</small>
        <strong>{{ item[1] }}</strong>
      </span>
    </div>

    <div class="assistant-trace-duration">
      <div class="assistant-trace-duration-head">
        <small>耗时拆分</small>
        <strong>{{ durationTotalText }}</strong>
      </div>
      <span
        v-for="item in durationItems"
        :key="`${messageId}-trace-duration-${item.code}`"
      >
        <b>{{ item.label }}</b>
        <em>{{ item.value }}</em>
      </span>
    </div>

    <div class="assistant-trace-grid">
      <article class="assistant-trace-block">
        <h3>查询改写</h3>
        <template v-if="trace.queryRewrite">
          <dl class="assistant-trace-kv">
            <div>
              <dt>原问题</dt>
              <dd>{{ traceText(trace.queryRewrite.originalQuery) }}</dd>
            </div>
            <div>
              <dt>改写后</dt>
              <dd>{{ traceText(trace.queryRewrite.rewrittenQuery) }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>
                {{ traceText(trace.queryRewrite.sourceType) }}
                / {{ traceSuccessText(trace.queryRewrite.success) }}
              </dd>
            </div>
          </dl>
          <div v-if="subQuestions.length" class="assistant-trace-list">
            <small>子问题</small>
            <span
              v-for="(question, questionIndex) in subQuestions"
              :key="`${messageId}-trace-question-${questionIndex}`"
            >
              {{ question }}
            </span>
          </div>
          <div v-if="matchedTerms.length" class="assistant-trace-tags">
            <small>术语命中</small>
            <span
              v-for="(term, termIndex) in matchedTerms"
              :key="`${messageId}-trace-term-${term.raw || termIndex}-${term.canonical || termIndex}`"
            >
              {{ term.raw }} → {{ term.canonical }}
            </span>
          </div>
        </template>
        <p v-else class="assistant-trace-empty">暂无查询改写记录</p>
      </article>

      <article class="assistant-trace-block">
        <h3>意图识别</h3>
        <template v-if="trace.intentResolve">
          <dl class="assistant-trace-kv compact">
            <div>
              <dt>结果</dt>
              <dd>
                {{ traceText(trace.intentResolve.outcome) }}
                / {{ traceSuccessText(trace.intentResolve.success) }}
              </dd>
            </div>
            <div>
              <dt>歧义</dt>
              <dd>{{ formatTraceBoolean(trace.intentResolve.ambiguous) }}</dd>
            </div>
          </dl>
          <div class="assistant-trace-tags">
            <small>最终意图</small>
            <span
              v-for="intent in intentLabels"
              :key="`${messageId}-trace-intent-${intent}`"
            >
              {{ intent }}
            </span>
          </div>
          <div v-if="selectedNodes.length" class="assistant-trace-node-list">
            <small>命中节点</small>
            <span
              v-for="(node, nodeIndex) in selectedNodes"
              :key="`${messageId}-trace-node-${node.nodeCode || nodeIndex}-${node.score || nodeIndex}`"
            >
              <b>{{ traceNodeTitle(node) }}</b>
              <em>{{ traceNodeMeta(node) }}</em>
            </span>
          </div>
          <p v-if="trace.intentResolve.guidanceQuestion" class="assistant-trace-note">
            {{ trace.intentResolve.guidanceQuestion }}
          </p>
        </template>
        <p v-else class="assistant-trace-empty">暂无意图识别记录</p>
      </article>
    </div>

    <footer class="assistant-trace-footer">
      <span>RAG：{{ ragText }}</span>
      <span>模型：{{ traceText(trace.modelId) }}</span>
    </footer>
  </section>
</template>
