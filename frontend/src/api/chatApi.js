const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

async function parseResponse(response, fallbackMessage) {
  if (response.ok) {
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

export async function fetchModels() {
  const response = await fetch(`${API_BASE_URL}/models`, {
    credentials: 'include'
  });

  return parseResponse(response, '模型列表加载失败');
}

export async function sendChatMessage(payload) {
  const response = await fetch(`${API_BASE_URL}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });

  return parseResponse(response, '消息发送失败');
}

export async function fetchConversations() {
  const response = await fetch(`${API_BASE_URL}/conversations`, {
    credentials: 'include'
  });

  return parseResponse(response, '会话列表加载失败');
}

export async function fetchConversationDetail(conversationId) {
  const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}`, {
    credentials: 'include'
  });

  return parseResponse(response, '会话消息加载失败');
}
