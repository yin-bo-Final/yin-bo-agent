const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

async function parseResponse(response, fallbackMessage) {
  if (response.ok) {
    if (response.status === 204) {
      return null;
    }
    return response.json();
  }

  let message = fallbackMessage;
  try {
    const errorBody = await response.json();
    if (errorBody?.message) {
      message = errorBody.message;
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

export async function fetchKnowledgeDocuments(knowledgeBaseId) {
  const response = await fetch(`${API_BASE_URL}/admin/knowledge/bases/${knowledgeBaseId}/documents`, {
    credentials: 'include'
  });
  return parseResponse(response, '文档列表加载失败');
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
