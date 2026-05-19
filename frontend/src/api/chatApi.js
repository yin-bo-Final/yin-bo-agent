const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const CHAT_REQUEST_TIMEOUT_MS = 45000;

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
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), CHAT_REQUEST_TIMEOUT_MS);

  let response;
  try {
    response = await fetch(`${API_BASE_URL}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify(payload),
      signal: controller.signal
    });
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('模型响应超时，请点击“新对话”后重试，或者稍后再发一次。');
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }

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
