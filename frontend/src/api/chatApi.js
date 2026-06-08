import { parseResponse } from './http';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const CHAT_REQUEST_TIMEOUT_MS = 45000;

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

export async function streamChatMessage(payload, handlers = {}) {
  const response = await fetch(`${API_BASE_URL}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    },
    credentials: 'include',
    body: JSON.stringify(payload),
    signal: handlers.signal
  });

  if (!response.ok) {
    await parseResponse(response, '消息发送失败');
    return;
  }

  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应读取。');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let completed = false;
  let streamError = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() || '';
    events.forEach((rawEvent) => {
      const event = handleSseEvent(rawEvent, handlers);
      if (event?.type === 'done') {
        completed = true;
      }
      if (event?.type === 'error') {
        streamError = event.error || '流式响应失败了，请稍后重试。';
      }
    });
  }

  buffer += decoder.decode();
  if (buffer.trim()) {
    const event = handleSseEvent(buffer, handlers);
    if (event?.type === 'done') {
      completed = true;
    }
    if (event?.type === 'error') {
      streamError = event.error || '流式响应失败了，请稍后重试。';
    }
  }

  return {
    completed,
    error: streamError
  };
}

function handleSseEvent(rawEvent, handlers) {
  const dataLines = rawEvent
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart());

  if (dataLines.length === 0) {
    return null;
  }

  let event;
  try {
    event = JSON.parse(dataLines.join('\n'));
  } catch (_error) {
    return null;
  }

  if (event.type === 'start') {
    handlers.onStart?.(event);
    return event;
  }
  if (event.type === 'delta') {
    handlers.onDelta?.(event);
    return event;
  }
  if (event.type === 'done') {
    handlers.onDone?.(event);
    return event;
  }
  if (event.type === 'error') {
    handlers.onError?.(event);
    return event;
  }
  return event;
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

export async function compressConversationMemory(conversationId) {
  const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}/memory/compress`, {
    method: 'POST',
    credentials: 'include'
  });

  return parseResponse(response, '会话记忆压缩失败');
}

export async function updateConversationPin(conversationId, pinned) {
  if (!pinned) {
    return unpinConversation(conversationId);
  }
  const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}/pin`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify({ pinned })
  });

  return parseResponse(response, '会话置顶状态更新失败');
}

export async function unpinConversation(conversationId) {
  const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}/unpin`, {
    method: 'POST',
    credentials: 'include'
  });

  return parseResponse(response, '取消置顶失败');
}

export async function deleteConversation(conversationId) {
  const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}`, {
    method: 'DELETE',
    credentials: 'include'
  });

  if (!response.ok) {
    return parseResponse(response, '会话删除失败');
  }
  return null;
}
