export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = options.status;
    this.requestId = options.requestId;
    this.body = options.body;
  }
}

export async function parseResponse(response, fallbackMessage) {
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

  const errorBody = await readErrorBody(response, contentType);
  const message = errorBody?.message || fallbackMessage;
  const error = new ApiError(message, {
    status: response.status,
    requestId: response.headers.get('X-Request-Id') || errorBody?.requestId,
    body: errorBody
  });

  notifyApiError(error);
  throw error;
}

async function readErrorBody(response, contentType) {
  try {
    const bodyText = await response.text();
    if (!bodyText) {
      return null;
    }
    if (contentType.includes('application/json')) {
      return JSON.parse(bodyText);
    }
    return { message: bodyText };
  } catch (_error) {
    return null;
  }
}

function notifyApiError(error) {
  if (typeof window === 'undefined' || error.status !== 429) {
    return;
  }
  window.dispatchEvent(new CustomEvent('yinbo-api-error', {
    detail: {
      status: error.status,
      message: error.message,
      requestId: error.requestId
    }
  }));
}
