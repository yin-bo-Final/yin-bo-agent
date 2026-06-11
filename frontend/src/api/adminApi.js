import { parseResponse } from './http';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export async function fetchAdminDashboard(options = {}) {
  const searchParams = new URLSearchParams();
  if (options.messageRange) {
    searchParams.set('messageRange', options.messageRange);
  }
  const query = searchParams.toString();
  const response = await fetch(`${API_BASE_URL}/admin/dashboard${query ? `?${query}` : ''}`, {
    credentials: 'include'
  });
  return parseResponse(response, 'Dashboard 加载失败');
}

export async function fetchKnowledgeOverview() {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/overview`, {
    credentials: 'include'
  });
  return parseResponse(response, '知识库概览加载失败');
}

export async function fetchKnowledgeBases() {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases`, {
    credentials: 'include'
  });
  return parseResponse(response, '知识库列表加载失败');
}

export async function fetchKnowledgeBase(knowledgeBaseId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases/${knowledgeBaseId}`, {
    credentials: 'include'
  });
  return parseResponse(response, '知识库详情加载失败');
}

export async function createKnowledgeBase(payload) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '知识库创建失败');
}

export async function deleteKnowledgeBase(knowledgeBaseId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases/${knowledgeBaseId}`, {
    method: 'DELETE',
    credentials: 'include'
  });
  return parseResponse(response, '知识库删除失败');
}

export async function updateKnowledgeBase(knowledgeBaseId, payload) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases/${knowledgeBaseId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '知识库更新失败');
}

export async function fetchKnowledgeDocuments(knowledgeBaseId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases/${knowledgeBaseId}/documents`, {
    credentials: 'include'
  });
  return parseResponse(response, '文档列表加载失败');
}

export async function fetchKnowledgeDocument(documentId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/documents/${documentId}`, {
    credentials: 'include'
  });
  return parseResponse(response, '文档详情加载失败');
}

export async function uploadKnowledgeDocument(knowledgeBaseId, payload) {
  const formData = new FormData();
  formData.append('file', payload.file);
  appendIngestionOptions(formData, payload);

  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases/${knowledgeBaseId}/documents/upload`, {
    method: 'POST',
    credentials: 'include',
    body: formData
  });
  return parseResponse(response, '文件上传失败');
}

export async function ingestKnowledgeUrl(knowledgeBaseId, payload) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases/${knowledgeBaseId}/documents/url`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, 'URL 解析失败');
}

export async function rechunkKnowledgeDocument(documentId, payload) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/documents/${documentId}/rechunk`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '文档重新分块失败');
}

export async function rebuildDocumentVectors(documentId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/documents/${documentId}/vectors/rebuild`, {
    method: 'POST',
    credentials: 'include'
  });
  return parseResponse(response, '向量重建失败');
}

export async function fetchFailedIngestionTasks(options = {}) {
  const query = buildRecordQuery(options, {
    statusKey: 'status'
  });
  const response = await fetch(`${API_BASE_URL}/admin/ingestion/tasks/failed${query ? `?${query}` : ''}`, {
    credentials: 'include'
  });
  return parseResponse(response, '失败任务列表加载失败');
}

export async function fetchTerminologyMappings() {
  const response = await fetch(`${API_BASE_URL}/admin/query/terminology/mappings`, {
    credentials: 'include'
  });
  return parseResponse(response, '关键词映射加载失败');
}

export async function createTerminologyMapping(payload) {
  const response = await fetch(`${API_BASE_URL}/admin/query/terminology/mappings`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '关键词映射创建失败');
}

export async function updateTerminologyMapping(aliasId, payload) {
  const response = await fetch(`${API_BASE_URL}/admin/query/terminology/mappings/${pathSegment(aliasId)}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '关键词映射更新失败');
}

export async function updateTerminologyMappingEnabled(aliasId, enabled) {
  const response = await fetch(`${API_BASE_URL}/admin/query/terminology/mappings/${pathSegment(aliasId)}/enabled`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify({ enabled })
  });
  return parseResponse(response, '关键词映射状态更新失败');
}

export async function deleteTerminologyMapping(aliasId) {
  const response = await fetch(`${API_BASE_URL}/admin/query/terminology/mappings/${pathSegment(aliasId)}`, {
    method: 'DELETE',
    credentials: 'include'
  });
  return parseResponse(response, '关键词映射删除失败');
}

export async function fetchQueryPipelineConfig() {
  const response = await fetch(`${API_BASE_URL}/admin/query/pipeline/config`, {
    credentials: 'include'
  });
  return parseResponse(response, '流水线配置加载失败');
}

export async function fetchQueryRewriteRecords(options = {}) {
  const query = buildRecordQuery(options, {
    statusKey: 'sourceType',
    statusOptionKey: 'sourceType',
    successKey: 'success'
  });
  const response = await fetch(`${API_BASE_URL}/admin/query/rewrite-records${query ? `?${query}` : ''}`, {
    credentials: 'include'
  });
  return parseResponse(response, '查询改写记录加载失败');
}

export async function updateQueryPipelineConfig(payload) {
  const response = await fetch(`${API_BASE_URL}/admin/query/pipeline/config`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '流水线配置保存失败');
}

export async function fetchIntentTree() {
  const response = await fetch(`${API_BASE_URL}/admin/intents/tree`, {
    credentials: 'include'
  });
  return parseResponse(response, '意图树加载失败');
}

export async function fetchIntentNodes() {
  const response = await fetch(`${API_BASE_URL}/admin/intents/nodes`, {
    credentials: 'include'
  });
  return parseResponse(response, '意图列表加载失败');
}

export async function createIntentNode(payload) {
  const response = await fetch(`${API_BASE_URL}/admin/intents/nodes`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '意图节点创建失败');
}

export async function updateIntentNode(nodeId, payload) {
  const response = await fetch(`${API_BASE_URL}/admin/intents/nodes/${pathSegment(nodeId)}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '意图节点更新失败');
}

export async function updateIntentNodeEnabled(nodeId, enabled) {
  const response = await fetch(`${API_BASE_URL}/admin/intents/nodes/${pathSegment(nodeId)}/enabled`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify({ enabled })
  });
  return parseResponse(response, '意图节点状态更新失败');
}

export async function deleteIntentNode(nodeId) {
  const response = await fetch(`${API_BASE_URL}/admin/intents/nodes/${pathSegment(nodeId)}`, {
    method: 'DELETE',
    credentials: 'include'
  });
  return parseResponse(response, '意图节点删除失败');
}

export async function fetchIntentRules() {
  const response = await fetch(`${API_BASE_URL}/admin/intents/rules`, {
    credentials: 'include'
  });
  return parseResponse(response, '意图规则加载失败');
}

export async function fetchIntentResolveRecords(options = {}) {
  const searchParams = buildRecordSearchParams(options);
  if (options.outcome && options.outcome !== 'ALL') {
    searchParams.set('outcome', options.outcome);
  }
  if (options.ambiguous && options.ambiguous !== 'ALL') {
    searchParams.set('ambiguous', options.ambiguous);
  }
  const query = searchParams.toString();
  const response = await fetch(`${API_BASE_URL}/admin/intents/resolve-records${query ? `?${query}` : ''}`, {
    credentials: 'include'
  });
  return parseResponse(response, '意图识别记录加载失败');
}

export async function createIntentRule(payload) {
  const response = await fetch(`${API_BASE_URL}/admin/intents/rules`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '意图规则创建失败');
}

export async function updateIntentRule(ruleId, payload) {
  const response = await fetch(`${API_BASE_URL}/admin/intents/rules/${pathSegment(ruleId)}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '意图规则更新失败');
}

export async function updateIntentRuleEnabled(ruleId, enabled) {
  const response = await fetch(`${API_BASE_URL}/admin/intents/rules/${pathSegment(ruleId)}/enabled`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify({ enabled })
  });
  return parseResponse(response, '意图规则状态更新失败');
}

export async function deleteIntentRule(ruleId) {
  const response = await fetch(`${API_BASE_URL}/admin/intents/rules/${pathSegment(ruleId)}`, {
    method: 'DELETE',
    credentials: 'include'
  });
  return parseResponse(response, '意图规则删除失败');
}

export async function retryIngestionTask(taskId) {
  const response = await fetch(`${API_BASE_URL}/admin/ingestion/tasks/${taskId}/retry`, {
    method: 'POST',
    credentials: 'include'
  });
  return parseResponse(response, '失败任务重试失败');
}

export async function deleteIngestionTask(taskId) {
  const response = await fetch(`${API_BASE_URL}/admin/ingestion/tasks/${taskId}`, {
    method: 'DELETE',
    credentials: 'include'
  });
  return parseResponse(response, '失败任务删除失败');
}

export async function deleteKnowledgeDocument(documentId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/documents/${documentId}`, {
    method: 'DELETE',
    credentials: 'include'
  });
  return parseResponse(response, '文档删除失败');
}

export async function fetchDocumentChunks(documentId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/documents/${documentId}/chunks`, {
    credentials: 'include'
  });
  return parseResponse(response, '分块列表加载失败');
}

export async function updateDocumentChunksEnabled(documentId, enabled) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/documents/${documentId}/chunks/enabled`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify({ enabled })
  });
  return parseResponse(response, '分块状态更新失败');
}

export async function updateChunkEnabled(chunkId, enabled) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/chunks/${chunkId}/enabled`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify({ enabled })
  });
  return parseResponse(response, '分块状态更新失败');
}

export async function updateChunk(chunkId, payload) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/chunks/${chunkId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });
  return parseResponse(response, '分块修改失败');
}

export async function deleteChunk(chunkId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/chunks/${chunkId}`, {
    method: 'DELETE',
    credentials: 'include'
  });
  return parseResponse(response, '分块删除失败');
}

function appendIngestionOptions(formData, payload) {
  ['strategy', 'chunkSize', 'chunkOverlap', 'maxChunks'].forEach((key) => {
    const value = payload[key];
    if (value !== null && value !== undefined && value !== '') {
      formData.append(key, value);
    }
  });
}

function buildRecordQuery(options, config = {}) {
  return buildRecordSearchParams(options, config).toString();
}

function buildRecordSearchParams(options, config = {}) {
  const searchParams = new URLSearchParams();
  if (options.page) {
    searchParams.set('page', options.page);
  }
  if (options.pageSize) {
    searchParams.set('pageSize', options.pageSize);
  }
  if (options.keyword) {
    searchParams.set('keyword', options.keyword);
  }
  const statusOptionKey = config.statusOptionKey || config.statusKey;
  if (config.statusKey && options[statusOptionKey] && options[statusOptionKey] !== 'ALL') {
    searchParams.set(config.statusKey, options[statusOptionKey]);
  }
  if (config.successKey && options[config.successKey] && options[config.successKey] !== 'ALL') {
    searchParams.set(config.successKey, options[config.successKey]);
  }
  if (options.startAt) {
    searchParams.set('startAt', normalizeDateTimeValue(options.startAt));
  }
  if (options.endAt) {
    searchParams.set('endAt', normalizeDateTimeValue(options.endAt));
  }
  return searchParams;
}

function normalizeDateTimeValue(value) {
  const text = String(value || '').trim();
  return text.length === 16 ? `${text}:00` : text;
}

function pathSegment(value) {
  return encodeURIComponent(String(value));
}
