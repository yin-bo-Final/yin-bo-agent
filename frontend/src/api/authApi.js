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

export async function login(payload) {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });

  return parseResponse(response, '登录失败');
}

export async function register(payload) {
  const response = await fetch(`${API_BASE_URL}/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });

  return parseResponse(response, '注册失败');
}

export async function fetchCurrentUser() {
  const response = await fetch(`${API_BASE_URL}/auth/me`, {
    credentials: 'include'
  });

  return parseResponse(response, '登录状态校验失败');
}

export async function logout() {
  const response = await fetch(`${API_BASE_URL}/auth/logout`, {
    method: 'POST',
    credentials: 'include'
  });

  return parseResponse(response, '退出登录失败');
}

export async function cancelAccount(payload) {
  const response = await fetch(`${API_BASE_URL}/auth/cancel`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });

  return parseResponse(response, '账号注销失败');
}
