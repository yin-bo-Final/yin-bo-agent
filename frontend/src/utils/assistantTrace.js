function traceValue(value, fallback = '-') {
  if (value === null || value === undefined || value === '') {
    return fallback;
  }
  return String(value);
}

export function hasAssistantTrace(message) {
  return message?.role === 'assistant' && Boolean(message.assistantTrace);
}

export function normalizeAssistantTrace(trace, message = {}, fallbackModelId = null) {
  if (!trace || typeof trace !== 'object') {
    return null;
  }
  return {
    ...trace,
    traceVersion: normalizeTraceVersion(trace.traceVersion),
    modelId: trace.modelId || message.modelId || fallbackModelId || null,
    responseDurationMs: trace.responseDurationMs ?? message.responseDurationMs ?? null,
    llmDurationMs: trace.llmDurationMs ?? null,
    otherDurationMs: trace.otherDurationMs ?? null,
    totalTokens: trace.totalTokens ?? message.totalTokens ?? null,
    enteredRag: Boolean(trace.enteredRag),
    queryRewrite: normalizeTraceObject(trace.queryRewrite),
    intentResolve: normalizeTraceObject(trace.intentResolve),
    rag: normalizeTraceObject(trace.rag),
    durationStages: normalizeDurationStages(trace)
  };
}

function normalizeTraceVersion(value) {
  const version = Number(value);
  return Number.isInteger(version) && version > 0 ? version : 1;
}

function normalizeTraceObject(value) {
  return value && typeof value === 'object' ? value : null;
}

function normalizeDurationStages(trace) {
  const stages = Array.isArray(trace?.durationStages) ? trace.durationStages : [];
  if (stages.length) {
    return stages
      .filter((stage) => stage && typeof stage === 'object')
      .map((stage, index) => normalizeDurationStage(stage, index));
  }
  return legacyDurationStages(trace);
}

function normalizeDurationStage(stage, index) {
  const code = traceValue(stage.code, `stage_${index + 1}`);
  return {
    code,
    label: traceValue(stage.label, code),
    durationMs: stage.durationMs ?? null
  };
}

function legacyDurationStages(trace) {
  return [
    ['query_rewrite', '查询改写', trace?.queryRewrite?.durationMs],
    ['intent_resolve', '意图识别', trace?.intentResolve?.durationMs],
    ['rag', 'RAG', trace?.rag?.durationMs],
    ['llm', 'LLM', trace?.llmDurationMs],
    ['other', '其他', trace?.otherDurationMs]
  ]
    .filter(([, , durationMs]) => durationMs !== null && durationMs !== undefined && durationMs !== '')
    .map(([code, label, durationMs]) => ({ code, label, durationMs }));
}
