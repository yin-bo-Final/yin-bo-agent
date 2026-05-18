const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export async function fetchModels() {
  const response = await fetch(`${API_BASE_URL}/models`);

  if (!response.ok) {
    throw new Error('模型列表加载失败');
  }

  return response.json();
}

export async function sendChatMessage(payload) {
  const response = await fetch(`${API_BASE_URL}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error('消息发送失败');
  }

  return response.json();
}
