const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

async function parseResponse(response, fallbackMessage) {
  const contentLength = response.headers.get('content-length');
  const contentType = response.headers.get('content-type') || '';

  if (response.ok) {
    if (response.status === 204 || contentLength === '0') {
      return null;
    }
    const bodyText = await response.text();
    if (!bodyText) {
      return null;
    }
    return contentType.includes('application/json') ? JSON.parse(bodyText) : bodyText;
  }

  let message = fallbackMessage;
  try {
    const bodyText = await response.text();
    const errorBody = bodyText && contentType.includes('application/json') ? JSON.parse(bodyText) : null;
    if (errorBody?.message) {
      message = errorBody.message;
    } else if (bodyText && !contentType.includes('application/json')) {
      message = bodyText;
    }
  } catch (_error) {
    message = fallbackMessage;
  }

  throw new Error(message);
}

export async function fetchAdminDashboard() {
  const response = await fetch(`${API_BASE_URL}/admin/dashboard`, {
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
