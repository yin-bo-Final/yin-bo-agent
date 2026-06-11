export const trendTypeOptions = [
  { type: 'message', label: '消息' },
  { type: 'conversation', label: '会话' },
  { type: 'responseTime', label: '响应时间' },
  { type: 'activeUser', label: '活跃用户' }
];

export function parseAdminRoute(pathname = window.location.pathname) {
  const segments = pathname.split('/').filter(Boolean);
  if (segments[0] !== 'admin') {
    return { module: 'dashboard', view: 'dashboard' };
  }
  if (segments[1] === 'tasks') {
    return { module: 'tasks', view: 'failed-tasks' };
  }
  if (segments[1] === 'mappings') {
    return { module: 'mappings', view: 'mappings' };
  }
  if (segments[1] === 'pipeline') {
    return { module: 'pipeline', view: 'pipeline' };
  }
  if (segments[1] === 'query-records') {
    return { module: 'query-records', view: 'query-records' };
  }
  if (segments[1] === 'intent-tree') {
    return { module: 'intent-tree', view: 'intent-tree' };
  }
  if (segments[1] === 'intent-list') {
    return { module: 'intent-list', view: 'intent-list' };
  }
  if (segments[1] === 'intent-rules') {
    return { module: 'intent-rules', view: 'intent-rules' };
  }
  if (segments[1] === 'intent-records') {
    return { module: 'intent-records', view: 'intent-records' };
  }
  if (segments[1] !== 'knowledge') {
    return { module: 'dashboard', view: 'dashboard' };
  }
  if (segments[2] && segments[3] === 'docs' && segments[4]) {
    return {
      module: 'knowledge',
      view: 'chunks',
      knowledgeBaseId: decodeURIComponent(segments[2]),
      documentId: decodeURIComponent(segments[4])
    };
  }
  if (segments[2]) {
    return {
      module: 'knowledge',
      view: 'documents',
      knowledgeBaseId: decodeURIComponent(segments[2])
    };
  }
  return { module: 'knowledge', view: 'bases' };
}

export function statusFilterText(value) {
  if (value === 'ENABLED') {
    return '启用';
  }
  if (value === 'DISABLED') {
    return '禁用';
  }
  return '全部状态';
}

export function documentStatusFilterText(value) {
  if (value === 'UPLOADING') {
    return 'uploading';
  }
  if (value === 'UPLOADED') {
    return 'uploaded';
  }
  if (value === 'COMPLETED') {
    return 'success';
  }
  if (value === 'PROCESSING') {
    return 'processing';
  }
  if (value === 'FAILED') {
    return 'failed';
  }
  return '全部状态';
}

export function taskStatusFilterText(value) {
  if (value === 'FAILED') {
    return 'failed';
  }
  if (value === 'DEAD') {
    return 'dead';
  }
  return '全部状态';
}

export function defaultMappingForm() {
  return {
    aliasName: '',
    canonicalName: '',
    termType: 'TECH',
    description: '',
    priority: 100,
    enabled: true
  };
}

export function defaultPipelineForm() {
  return {
    terminologyEnabled: true,
    llmRewriteEnabled: true,
    ruleSplitEnabled: true,
    fallbackPolicy: 'TERM_ONLY',
    rewriteTimeoutMs: 3000,
    rewriteContextTurns: 3
  };
}

export function defaultIntentNodeForm() {
  return {
    nodeCode: '',
    parentCode: '',
    name: '',
    description: '',
    level: 'CATEGORY',
    kind: 'KB',
    examplesText: '',
    knowledgeBaseNo: '',
    collectionName: '',
    mcpToolId: '',
    promptSnippet: '',
    promptTemplate: '',
    paramPromptTemplate: '',
    topK: '',
    minScore: '',
    sortOrder: 0,
    enabled: true
  };
}

export function defaultIntentRuleForm() {
  return {
    ruleCode: '',
    name: '',
    description: '',
    targetNodeCode: '',
    ruleType: 'STRONG',
    includeKeywordsText: '',
    includeMatchMode: 'ANY',
    requireKeywordsText: '',
    requireMatchMode: 'ANY',
    excludeKeywordsText: '',
    score: 0.9,
    enabled: true
  };
}

export function defaultChunkOptions() {
  return {
    strategy: 'RECURSIVE',
    chunkSize: 1000,
    chunkOverlap: 150,
    maxChunks: 200
  };
}

export function normalizeChunkPayload(options) {
  const strategy = options.strategy || 'RECURSIVE';
  if (strategy === 'NONE') {
    return { strategy };
  }
  if (strategy === 'AUTO') {
    return { strategy };
  }
  return {
    strategy,
    chunkSize: Number(options.chunkSize || 1000),
    chunkOverlap: Number(options.chunkOverlap || 0),
    maxChunks: Number(options.maxChunks || 200)
  };
}

export function normalizeIntentNodePayload(form) {
  const examples = String(form.examplesText || '')
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter(Boolean);
  return {
    nodeCode: form.nodeCode.trim(),
    parentCode: form.parentCode.trim(),
    name: form.name.trim(),
    description: form.description.trim(),
    level: form.level,
    kind: form.kind,
    examples,
    knowledgeBaseNo: form.knowledgeBaseNo.trim(),
    collectionName: form.collectionName.trim(),
    mcpToolId: form.mcpToolId.trim(),
    promptSnippet: form.promptSnippet.trim(),
    promptTemplate: form.promptTemplate.trim(),
    paramPromptTemplate: form.paramPromptTemplate.trim(),
    topK: form.topK === '' ? null : Number(form.topK),
    minScore: form.minScore === '' ? null : Number(form.minScore),
    sortOrder: Number(form.sortOrder || 0),
    enabled: form.enabled !== false
  };
}

export function normalizeIntentRulePayload(form) {
  return {
    ruleCode: form.ruleCode.trim(),
    name: form.name.trim(),
    description: form.description.trim(),
    targetNodeCode: form.targetNodeCode.trim(),
    ruleType: form.ruleType,
    includeKeywords: keywordArray(form.includeKeywordsText),
    includeMatchMode: form.includeMatchMode || 'ANY',
    requireKeywords: keywordArray(form.requireKeywordsText),
    requireMatchMode: form.requireMatchMode || 'ANY',
    excludeKeywords: keywordArray(form.excludeKeywordsText),
    score: form.score === '' ? null : Number(form.score),
    enabled: form.enabled !== false
  };
}

export function normalizePipelinePayload(value) {
  return {
    terminologyEnabled: value?.terminologyEnabled !== false,
    llmRewriteEnabled: value?.llmRewriteEnabled !== false,
    ruleSplitEnabled: value?.ruleSplitEnabled !== false,
    fallbackPolicy: value?.fallbackPolicy || 'TERM_ONLY',
    rewriteTimeoutMs: Number(value?.rewriteTimeoutMs || 3000),
    rewriteContextTurns: Number(value?.rewriteContextTurns || 3)
  };
}

export function intentKindLabel(value) {
  if (value === 'MCP') {
    return 'MCP 工具';
  }
  if (value === 'SYSTEM') {
    return '系统直答';
  }
  return '知识库';
}

export function intentLevelLabel(value) {
  if (value === 'DOMAIN') {
    return '领域';
  }
  if (value === 'TOPIC') {
    return '主题';
  }
  return '分类';
}

export function intentRuleTypeLabel(value) {
  return value === 'WEAK' ? '弱规则' : '强规则';
}

export function intentMatchModeLabel(value) {
  return value === 'ALL' ? '全部命中' : '任一命中';
}

export function intentExamplesText(examples) {
  return Array.isArray(examples) ? examples.join('\n') : '';
}

export function keywordText(keywords) {
  return Array.isArray(keywords) ? keywords.join('\n') : '';
}

export function keywordArray(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter(Boolean);
}

export function formatNumber(value) {
  return Number(value || 0).toLocaleString();
}

export function formatBytes(value) {
  const bytes = Number(value || 0);
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '待生成';
}

export function formatDuration(value) {
  if (value === null || value === undefined) {
    return '待接入';
  }
  if (value < 1000) {
    return `${value}ms`;
  }
  return `${(value / 1000).toFixed(2)}s`;
}

export function formatTrendValue(value, unit) {
  const numberValue = Number(value || 0);
  if (unit === '毫秒') {
    return formatDuration(numberValue);
  }
  return formatNumber(numberValue);
}

export function formatTrendAxisValue(value, unit) {
  const numberValue = Number(value || 0);
  if (unit === '毫秒') {
    if (numberValue >= 1000) {
      const seconds = numberValue / 1000;
      return `${seconds >= 10 ? seconds.toFixed(0) : seconds.toFixed(1)}s`;
    }
    return `${Math.round(numberValue)}ms`;
  }
  return formatNumber(Math.round(numberValue));
}

export function metricText(value, fallback = '待接入') {
  return value === null || value === undefined ? fallback : formatNumber(value);
}

export function sourceText(document) {
  return document.sourceType === 'URL' ? 'URL' : 'Local File';
}

export function taskActionText(action) {
  if (action === 'REBUILD_VECTORS') {
    return '重建向量';
  }
  return '分块';
}

export function taskStatusText(status) {
  if (status === 'DEAD') {
    return 'dead';
  }
  if (status === 'FAILED') {
    return 'failed';
  }
  if (status === 'RETRYING') {
    return 'retrying';
  }
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'RUNNING') {
    return 'running';
  }
  return 'pending';
}

export function taskStatusClass(status) {
  if (status === 'DEAD' || status === 'FAILED') {
    return 'danger';
  }
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'RETRYING' || status === 'RUNNING') {
    return 'pending';
  }
  return 'muted';
}

export function taskOptionsText(task) {
  if (task.action === 'REBUILD_VECTORS') {
    return '沿用当前分块';
  }
  if (task.strategy === 'RECURSIVE') {
    return `${task.strategy.toLowerCase()} / ${task.chunkSize}-${task.chunkOverlap} / ${task.maxChunks}`;
  }
  return task.strategy ? task.strategy.toLowerCase() : '-';
}

export function canRetryTask(task) {
  return task?.status === 'FAILED';
}

export function isSessionError(error) {
  return error.message?.includes('未登录') || error.message?.includes('会话已过期');
}

export function taskDetailRows(task) {
  if (!task) {
    return [];
  }
  return [
    ['任务编号', task.taskId],
    ['文档编号', task.documentId],
    ['文档状态', task.documentStatus],
    ['动作', taskActionText(task.action)],
    ['状态', taskStatusText(task.status)],
    ['分块参数', taskOptionsText(task)],
    ['重试次数', `${task.retryCount} / ${task.maxRetries}`],
    ['最近失败', formatDate(task.lastFailedAt || task.updatedAt)],
    ['开始时间', task.lastStartedAt ? formatDate(task.lastStartedAt) : '-'],
    ['创建时间', formatDate(task.createdAt)],
    ['更新时间', formatDate(task.updatedAt)],
    ['MQ MessageId', task.mqMessageId || '-'],
    ['Source RequestId', task.sourceRequestId || '-'],
    ['错误原因', task.lastError || '-']
  ];
}

export function typeText(document) {
  const type = document.contentType || document.fileName?.split('.').pop() || document.sourceType;
  return String(type).replace('application/', '').replace('text/', '');
}

export function statusClass(status) {
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'danger';
  }
  if (status === 'UPLOADED') {
    return 'muted';
  }
  if (status === 'UPLOADING') {
    return 'pending';
  }
  return 'pending';
}

export function statusText(status) {
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'failed';
  }
  if (status === 'UPLOADED') {
    return 'uploaded';
  }
  if (status === 'UPLOADING') {
    return 'uploading';
  }
  return 'processing';
}

export function isBusyDocumentStatus(status) {
  return status === 'UPLOADING' || status === 'PROCESSING';
}

export function isFailedDocumentStatus(status) {
  return status === 'FAILED';
}

export function shouldShowDocumentChunkAction(document) {
  return !isFailedDocumentStatus(document?.status);
}

export function canRechunkDocument(document) {
  return document
    && shouldShowDocumentChunkAction(document)
    && !isBusyDocumentStatus(document.status);
}

export function canOpenDocumentChunks(document) {
  return document
    && !isFailedDocumentStatus(document.status)
    && document.status !== 'UPLOADING';
}

export function canViewDocumentChunks(document) {
  return canOpenDocumentChunks(document) && Number(document.chunkCount || 0) > 0;
}

export function documentChunkActionLabel(document) {
  if (document.status === 'UPLOADING') {
    return '上传中...';
  }
  if (document.status === 'PROCESSING') {
    return '处理中...';
  }
  if (document.status === 'COMPLETED') {
    return '重新分块';
  }
  return '分块';
}
