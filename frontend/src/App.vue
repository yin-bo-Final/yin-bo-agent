<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { cancelAccount, fetchCurrentUser, login, logout, register } from './api/authApi';
import {
  deleteConversation as deleteConversationRequest,
  fetchConversationDetail,
  fetchConversations,
  fetchModels,
  streamChatMessage,
  updateConversationPin
} from './api/chatApi';

const fallbackModels = [
  {
    id: 'deepseek-ai/DeepSeek-V4-Flash',
    name: 'DeepSeek V4 Flash',
    provider: 'siliconflow',
    enabled: true
  },
  {
    id: 'Pro/moonshotai/Kimi-K2.6',
    name: 'Kimi K2.6 Pro',
    provider: 'siliconflow',
    enabled: true
  },
  {
    id: 'Pro/zai-org/GLM-5.1',
    name: 'GLM 5.1 Pro',
    provider: 'siliconflow',
    enabled: true
  },
  {
    id: 'Pro/MiniMaxAI/MiniMax-M2.5',
    name: 'MiniMax M2.5 Pro',
    provider: 'siliconflow',
    enabled: true
  },
  {
    id: 'Qwen/Qwen3.6-27B',
    name: 'Qwen3.6 27B',
    provider: 'siliconflow',
    enabled: true
  }
];

const models = ref(fallbackModels);
const selectedModelId = ref(fallbackModels[0].id);
const currentUser = ref(null);
const messages = ref(buildLoggedOutMessages());
const inputText = ref('');
const conversationId = ref('');
const conversations = ref([]);
const isSending = ref(false);
const isCheckingLogin = ref(true);
const isLoggingIn = ref(false);
const isRegistering = ref(false);
const isCancellingAccount = ref(false);
const isLoadingConversations = ref(false);
const isLoadingConversationDetail = ref(false);
const loadError = ref('');
const loginError = ref('');
const cancelError = ref('');
const conversationError = ref('');
const authMode = ref('login');
const authForm = ref({
  username: '',
  password: ''
});
const isPasswordVisible = ref(false);
const isUserMenuOpen = ref(false);
const isCancelMenuOpen = ref(false);
const isSidebarCollapsed = ref(false);
const isRecentPopoverOpen = ref(false);
const isModelMenuOpen = ref(false);
const conversationOpenMenuId = ref('');
const deleteDialog = ref({
  open: false,
  conversation: null
});
const isDeletingConversation = ref(false);
const deleteDialogError = ref('');
const thinkMode = ref(false);
const searchText = ref('');
const cancelForm = ref({
  password: ''
});
const messageList = ref(null);
const searchInput = ref(null);
const modelMenu = ref(null);
const shouldAutoScroll = ref(true);
const activeStreamController = ref(null);
const activeStreamAnimator = ref(null);
const activeAssistantMessage = ref(null);
const authPointer = ref({
  x: 0,
  y: 0
});
let pendingAuthPointer = { x: 0, y: 0 };
let pointerAnimationFrame = 0;
const renderedMessageCache = new WeakMap();

const selectedModel = computed(() => {
  return models.value.find((model) => model.id === selectedModelId.value) || models.value[0];
});
const groupedModels = computed(() => {
  const groupedMap = new Map();
  models.value.forEach((model) => {
    const providerName = model.provider || '默认供应商';
    if (!groupedMap.has(providerName)) {
      groupedMap.set(providerName, []);
    }
    groupedMap.get(providerName).push(model);
  });
  return Array.from(groupedMap.entries()).map(([provider, items]) => ({
    provider,
    items
  }));
});
const authPageStyle = computed(() => ({
  '--pointer-x': `${authPointer.value.x}px`,
  '--pointer-y': `${authPointer.value.y}px`
}));

const activeConversationTitle = computed(() => {
  const matchedConversation = conversations.value.find((item) => item.conversationId === conversationId.value);
  return matchedConversation?.title || '新会话';
});
const deleteDialogTitle = computed(() => {
  return deleteDialog.value.conversation?.title || '这个会话';
});
const filteredConversations = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  if (!keyword) {
    return conversations.value;
  }
  return conversations.value.filter((conversation) => {
    return conversation.title?.toLowerCase().includes(keyword);
  });
});
const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !isSending.value && !isLoadingConversationDetail.value;
});
const canUseComposer = computed(() => {
  return isSending.value || canSend.value;
});
const composerSubmitText = computed(() => {
  return isSending.value ? '中断' : '发送';
});
const isAuthenticated = computed(() => currentUser.value !== null);
const authSubmitText = computed(() => {
  if (authMode.value === 'login') {
    return isLoggingIn.value ? '登录中...' : '登录';
  }
  return isRegistering.value ? '注册中...' : '注册';
});
const isAuthSubmitting = computed(() => {
  return authMode.value === 'login' ? isLoggingIn.value : isRegistering.value;
});
const isFreshConversation = computed(() => {
  return isAuthenticated.value && !conversationId.value && messages.value.length === 0;
});
const hasStreamingAssistant = computed(() => {
  return messages.value.some((message) => message.role === 'assistant' && message.isStreaming);
});
const greetingName = computed(() => {
  return currentUser.value?.displayName || currentUser.value?.username || '朋友';
});

marked.setOptions({
  breaks: true,
  gfm: true
});

onMounted(async () => {
  const centerPoint = {
    x: window.innerWidth / 2,
    y: window.innerHeight / 2
  };
  pendingAuthPointer = centerPoint;
  authPointer.value = centerPoint;

  window.addEventListener('popstate', handlePopState);
  window.addEventListener('keydown', handleGlobalKeydown);
  window.addEventListener('pointerdown', handleWindowPointerDown);

  await restoreSession();

  try {
    const remoteModels = await fetchModels();
    if (Array.isArray(remoteModels) && remoteModels.length > 0) {
      const enabledModels = remoteModels.filter((model) => model.enabled !== false);
      models.value = mergeModels(enabledModels.length > 0 ? enabledModels : fallbackModels, fallbackModels);
      const matchedModel = models.value.find((model) => model.id === selectedModelId.value);
      selectedModelId.value = matchedModel?.id || models.value[0]?.id || fallbackModels[0].id;
    }
  } catch (error) {
    loadError.value = '后端未启动时会使用前端内置模型列表。';
  }
});

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState);
  window.removeEventListener('keydown', handleGlobalKeydown);
  window.removeEventListener('pointerdown', handleWindowPointerDown);
  stopPointerFrame();
});

async function restoreSession() {
  isCheckingLogin.value = true;
  try {
    const response = await fetchCurrentUser();
    currentUser.value = response.user;
    await loadConversations();
    await syncConversationWithRoute({ replaceHistory: true });
  } catch (_error) {
    currentUser.value = null;
    resetConversationState();
  } finally {
    isCheckingLogin.value = false;
  }
}

async function submitLogin() {
  if (isLoggingIn.value) {
    return;
  }

  loginError.value = '';
  isLoggingIn.value = true;
  try {
    const response = await login(authForm.value);
    currentUser.value = response.user;
    loginError.value = '';
    await loadConversations();
    await syncConversationWithRoute({ replaceHistory: true });
  } catch (error) {
    loginError.value = error.message;
  } finally {
    isLoggingIn.value = false;
  }
}

async function submitRegister() {
  if (isRegistering.value) {
    return;
  }

  loginError.value = '';
  isRegistering.value = true;
  try {
    const response = await register(authForm.value);
    currentUser.value = response.user;
    authMode.value = 'login';
    isPasswordVisible.value = false;
    await loadConversations();
    await syncConversationWithRoute({ replaceHistory: true });
  } catch (error) {
    loginError.value = error.message;
  } finally {
    isRegistering.value = false;
  }
}

async function submitMessage() {
  if (isSending.value) {
    interruptCurrentResponse();
    return;
  }

  if (!isAuthenticated.value) {
    loginError.value = '请先登录，再发送消息。';
    return;
  }

  if (!canSend.value) {
    return;
  }

  const content = inputText.value.trim();
  inputText.value = '';
  shouldAutoScroll.value = true;
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'user',
    content
  });

  isSending.value = true;
  conversationError.value = '';
  await scrollToBottom();

  const requestMessages = messages.value.map((message) => ({
    role: message.role,
    content: message.content
  }));
  const assistantMessageDraft = {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: '',
    isStreaming: true,
    thinkMode: thinkMode.value,
    thinkStartedAt: performance.now(),
    thinkDurationSeconds: null
  };
  messages.value.push(assistantMessageDraft);
  const assistantMessage = messages.value[messages.value.length - 1];
  const streamAnimator = createStreamAnimator(assistantMessage);
  const streamController = new AbortController();
  activeStreamController.value = streamController;
  activeStreamAnimator.value = streamAnimator;
  activeAssistantMessage.value = assistantMessage;

  try {
    const payload = {
      conversationId: conversationId.value,
      modelId: selectedModelId.value,
      thinkMode: thinkMode.value,
      messages: requestMessages
    };

    await streamChatMessage(payload, {
      signal: streamController.signal,
      onStart(event) {
        if (event.conversationId) {
          conversationId.value = event.conversationId;
          updateBrowserUrl(event.conversationId, { replace: true });
        }
      },
      onDelta(event) {
        streamAnimator.push(event.content || '');
      },
      onDone(event) {
        if (event.conversationId) {
          conversationId.value = event.conversationId;
          updateBrowserUrl(event.conversationId, { replace: true });
        }
      },
      onError(event) {
        streamAnimator.replace(event.error || '流式响应失败了，请稍后重试。');
      }
    });
    await streamAnimator.finish();
    finishThinkDuration(assistantMessage);
    assistantMessage.isStreaming = false;

    await loadConversations();
  } catch (error) {
    if (error.name === 'AbortError') {
      streamAnimator.replace(interruptedMessageContent(assistantMessage));
      return;
    }
    if (error.message.includes('未登录') || error.message.includes('会话已过期')) {
      currentUser.value = null;
      resetConversationState();
    }
    const assistantErrorMessage = error?.message?.trim()
      || '消息发送失败了。请确认你已经登录，并且后端、Redis、PostgreSQL 都已经启动。';
    streamAnimator.replace(assistantErrorMessage);
    if (!messages.value.includes(assistantMessage)) {
      messages.value.push(assistantMessage);
    }
  } finally {
    await streamAnimator.finish();
    finishThinkDuration(assistantMessage);
    assistantMessage.isStreaming = false;
    isSending.value = false;
    if (activeStreamController.value === streamController) {
      activeStreamController.value = null;
    }
    if (activeStreamAnimator.value === streamAnimator) {
      activeStreamAnimator.value = null;
    }
    if (activeAssistantMessage.value === assistantMessage) {
      activeAssistantMessage.value = null;
    }
    await scrollToBottom();
  }
}

function interruptCurrentResponse() {
  activeStreamController.value?.abort();
  if (activeStreamAnimator.value && activeAssistantMessage.value) {
    activeStreamAnimator.value.replace(interruptedMessageContent(activeAssistantMessage.value));
  }
}

function interruptedMessageContent(message) {
  if (!message.content) {
    return message.thinkMode ? '思考已中断。' : '回复已中断。';
  }
  return `${message.content.trimEnd()}\n\n[已中断]`;
}

function startNewChat() {
  conversationId.value = '';
  inputText.value = '';
  conversationError.value = '';
  isModelMenuOpen.value = false;
  conversationOpenMenuId.value = '';
  messages.value = buildNewConversationMessages();
  updateBrowserUrl('', { replace: false });
}

function startNewChatFromNavigation() {
  isRecentPopoverOpen.value = false;
  startNewChat();
}

async function handleLogout() {
  try {
    await logout();
  } finally {
    isUserMenuOpen.value = false;
    isCancelMenuOpen.value = false;
    isModelMenuOpen.value = false;
    currentUser.value = null;
    resetConversationState();
    loginError.value = '';
    messages.value = buildLoggedOutMessages('你已经退出登录。重新登录后可以继续使用对话功能。');
  }
}

async function handleCancelAccount() {
  if (isCancellingAccount.value) {
    return;
  }

  cancelError.value = '';
  isCancellingAccount.value = true;
  try {
    await cancelAccount(cancelForm.value);
    isUserMenuOpen.value = false;
    isCancelMenuOpen.value = false;
    currentUser.value = null;
    cancelForm.value.password = '';
    resetConversationState();
    messages.value = buildLoggedOutMessages('账号已经注销。这个用户名现在可以再次注册使用。');
  } catch (error) {
    cancelError.value = error.message;
  } finally {
    isCancellingAccount.value = false;
  }
}

async function submitAuthForm() {
  if (authMode.value === 'login') {
    await submitLogin();
    return;
  }
  await submitRegister();
}

function togglePasswordVisibility() {
  isPasswordVisible.value = !isPasswordVisible.value;
}

function toggleUserMenu() {
  isUserMenuOpen.value = !isUserMenuOpen.value;
  if (isUserMenuOpen.value) {
    isModelMenuOpen.value = false;
  } else {
    isCancelMenuOpen.value = false;
  }
}

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value;
  isRecentPopoverOpen.value = false;
  isModelMenuOpen.value = false;
  conversationOpenMenuId.value = '';
  if (isSidebarCollapsed.value) {
    isUserMenuOpen.value = false;
    isCancelMenuOpen.value = false;
  }
}

function openSidebar() {
  isSidebarCollapsed.value = false;
  isRecentPopoverOpen.value = false;
  isModelMenuOpen.value = false;
  isCancelMenuOpen.value = false;
}

function openUserMenuFromRail() {
  isSidebarCollapsed.value = false;
  isUserMenuOpen.value = true;
  isRecentPopoverOpen.value = false;
  isModelMenuOpen.value = false;
  isCancelMenuOpen.value = false;
}

async function openSearch() {
  isSidebarCollapsed.value = false;
  isRecentPopoverOpen.value = false;
  isModelMenuOpen.value = false;
  await nextTick();
  searchInput.value?.focus();
}

function toggleRecentPopover() {
  isModelMenuOpen.value = false;
  isRecentPopoverOpen.value = !isRecentPopoverOpen.value;
}

function toggleModelMenu() {
  isRecentPopoverOpen.value = false;
  isUserMenuOpen.value = false;
  isCancelMenuOpen.value = false;
  isModelMenuOpen.value = !isModelMenuOpen.value;
}

function toggleThinkMode() {
  thinkMode.value = !thinkMode.value;
}

function selectModel(modelId) {
  selectedModelId.value = modelId;
  isModelMenuOpen.value = false;
}

function mergeModels(primaryModels, fallbackModels) {
  const modelMap = new Map();
  [...primaryModels, ...fallbackModels].forEach((model) => {
    if (model?.id && !modelMap.has(model.id)) {
      modelMap.set(model.id, model);
    }
  });
  return Array.from(modelMap.values());
}

async function openConversationFromPopover(targetConversationId) {
  isRecentPopoverOpen.value = false;
  await openConversation(targetConversationId);
}

function formatConversationTime(value) {
  return value ? new Date(value).toLocaleString() : '刚刚';
}

function conversationMeta(conversation) {
  return formatConversationTime(conversation.lastMessageAt);
}

async function toggleConversationPin(conversation) {
  if (!conversation?.conversationId) {
    return;
  }
  conversationError.value = '';
  try {
    const updatedConversation = await updateConversationPin(conversation.conversationId, !conversation.pinned);
    conversations.value = conversations.value.map((item) => {
      return item.conversationId === updatedConversation.conversationId ? updatedConversation : item;
    });
    conversationOpenMenuId.value = '';
    await loadConversations();
  } catch (error) {
    conversationError.value = error.message;
  }
}

function openDeleteConversationDialog(conversation) {
  if (!conversation?.conversationId) {
    return;
  }
  if (isSending.value && conversation.conversationId === conversationId.value) {
    conversationError.value = 'AI 正在回复，先点击“中断”后再删除当前会话。';
    return;
  }
  conversationOpenMenuId.value = '';
  conversationError.value = '';
  deleteDialogError.value = '';
  deleteDialog.value = {
    open: true,
    conversation
  };
}

function closeDeleteConversationDialog() {
  if (isDeletingConversation.value) {
    return;
  }
  deleteDialog.value = {
    open: false,
    conversation: null
  };
  deleteDialogError.value = '';
}

async function confirmDeleteConversation() {
  const targetConversation = deleteDialog.value.conversation;
  if (!targetConversation?.conversationId || isDeletingConversation.value) {
    return;
  }
  if (isSending.value && targetConversation.conversationId === conversationId.value) {
    conversationError.value = 'AI 正在回复，先点击“中断”后再删除当前会话。';
    closeDeleteConversationDialog();
    return;
  }
  conversationError.value = '';
  deleteDialogError.value = '';
  isDeletingConversation.value = true;
  try {
    await deleteConversationRequest(targetConversation.conversationId);
    conversations.value = conversations.value.filter((item) => item.conversationId !== targetConversation.conversationId);
    if (targetConversation.conversationId === conversationId.value) {
      conversationId.value = '';
      inputText.value = '';
      conversationError.value = '';
      messages.value = buildNewConversationMessages();
      updateBrowserUrl('', { replace: true });
    }
    deleteDialog.value = {
      open: false,
      conversation: null
    };
    await loadConversations();
  } catch (error) {
    conversationError.value = error.message;
    deleteDialogError.value = error.message;
  } finally {
    isDeletingConversation.value = false;
  }
}

function handleGlobalKeydown(event) {
  if (event.key === 'Escape' && deleteDialog.value.open) {
    closeDeleteConversationDialog();
    return;
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    void openSearch();
  }
}

function handleWindowPointerDown(event) {
  if (isModelMenuOpen.value && modelMenu.value && !modelMenu.value.contains(event.target)) {
    isModelMenuOpen.value = false;
  }
  if (conversationOpenMenuId.value && !event.target.closest('.conversation-menu-host')) {
    conversationOpenMenuId.value = '';
  }
}

function toggleConversationMenu(conversationIdValue) {
  conversationOpenMenuId.value = conversationOpenMenuId.value === conversationIdValue ? '' : conversationIdValue;
}

function openCancelMenu() {
  if (isCancelMenuOpen.value) {
    closeCancelMenu();
    return;
  }
  cancelError.value = '';
  isCancelMenuOpen.value = true;
}

function closeCancelMenu() {
  isCancelMenuOpen.value = false;
  cancelError.value = '';
  cancelForm.value.password = '';
}

function renderMessageContent(message) {
  const cachedMessage = renderedMessageCache.get(message);
  if (
    cachedMessage?.content === message.content
    && cachedMessage.role === message.role
    && cachedMessage.isStreaming === Boolean(message.isStreaming)
    && cachedMessage.thinkMode === Boolean(message.thinkMode)
    && cachedMessage.thinkDurationSeconds === message.thinkDurationSeconds
  ) {
    return cachedMessage.html;
  }

  let html = '';
  if (message.role !== 'assistant') {
    html = escapeHtml(message.content || '');
  } else if (!message.content && message.isStreaming) {
    html = '<span class="stream-placeholder">正在思考</span>';
  } else if (shouldRenderThinkPanel(message)) {
    html = renderThinkMessage(message);
  } else if (message.isStreaming) {
    html = `${escapeHtml(message.content || '')}<span class="stream-cursor" aria-hidden="true"></span>`;
  } else {
    const renderedHtml = marked.parse(message.content || '');
    html = DOMPurify.sanitize(renderedHtml);
  }

  renderedMessageCache.set(message, {
    content: message.content,
    role: message.role,
    isStreaming: Boolean(message.isStreaming),
    thinkMode: Boolean(message.thinkMode),
    thinkDurationSeconds: message.thinkDurationSeconds,
    html
  });
  return html;
}

function shouldRenderThinkPanel(message) {
  return Boolean(message.thinkMode) || hasThinkHeading(message.content) || hasFinalAnswerHeading(message.content);
}

function renderThinkMessage(message) {
  const parts = splitThinkContent(message.content || '', Boolean(message.thinkMode));
  const thinkStatus = buildThinkStatus(message, Boolean(parts.answer));
  const thinkBody = renderContentBlock(parts.thinking, !message.isStreaming);
  const answerBody = renderContentBlock(parts.answer, !message.isStreaming);
  const cursor = message.isStreaming ? '<span class="stream-cursor" aria-hidden="true"></span>' : '';

  return `
    <section class="think-message">
      <details class="think-panel" open>
        <summary class="think-summary">
          ${message.isStreaming && !parts.answer
            ? '<span class="think-spinner" aria-hidden="true"></span>'
            : '<img class="think-logo" src="/yinbo-logo.svg" alt="" aria-hidden="true" />'}
          <span>${escapeHtml(thinkStatus)}</span>
          <span class="think-chevron" aria-hidden="true">⌄</span>
        </summary>
        <div class="think-content markdown-body">${thinkBody || '<span class="stream-placeholder">正在整理思考过程</span>'}</div>
      </details>
      ${parts.answer
        ? `<div class="think-answer markdown-body">${answerBody}${cursor}</div>`
        : (message.isStreaming ? `<div class="think-answer-pending">${cursor}</div>` : '')}
    </section>
  `;
}

function splitThinkContent(content, forceThinkPanel) {
  const thinkMatch = content.match(/(?:^|\n)\s*(?:\*\*)?思考(?:过程|摘要)?(?:\*\*)?\s*[:：]?\s*/);
  const answerMatch = content.match(/(?:^|\n)\s*(?:\*\*)?(?:最终回答|回答)(?:\*\*)?\s*[:：]?\s*/);

  if (thinkMatch && answerMatch && answerMatch.index > thinkMatch.index) {
    return {
      thinking: content.slice(thinkMatch.index + thinkMatch[0].length, answerMatch.index).trim(),
      answer: content.slice(answerMatch.index + answerMatch[0].length).trim()
    };
  }

  if (thinkMatch) {
    return {
      thinking: content.slice(thinkMatch.index + thinkMatch[0].length).trim(),
      answer: ''
    };
  }

  if (answerMatch) {
    return {
      thinking: content.slice(0, answerMatch.index).trim(),
      answer: content.slice(answerMatch.index + answerMatch[0].length).trim()
    };
  }

  return {
    thinking: forceThinkPanel ? content.trim() : '',
    answer: forceThinkPanel ? '' : content.trim()
  };
}

function renderContentBlock(content, allowMarkdown) {
  if (!content) {
    return '';
  }
  if (!allowMarkdown) {
    return escapeHtml(content);
  }
  return DOMPurify.sanitize(marked.parse(content));
}

function buildThinkStatus(message, hasAnswer) {
  if (message.isStreaming && !hasAnswer) {
    return '正在思考';
  }
  const seconds = message.thinkDurationSeconds;
  return seconds ? `已思考（用时 ${seconds} 秒）` : '已思考';
}

function hasThinkHeading(content = '') {
  return /(?:^|\n)\s*(?:\*\*)?思考(?:过程|摘要)?(?:\*\*)?\s*[:：]?/.test(content);
}

function hasFinalAnswerHeading(content = '') {
  return /(?:^|\n)\s*(?:\*\*)?(?:最终回答|回答)(?:\*\*)?\s*[:：]?/.test(content);
}

function updateThinkDuration(message) {
  if (!message.thinkMode || message.thinkDurationSeconds) {
    return;
  }
  if (hasFinalAnswerHeading(message.content)) {
    finishThinkDuration(message);
  }
}

function finishThinkDuration(message) {
  if (!message.thinkMode || message.thinkDurationSeconds || !message.thinkStartedAt) {
    return;
  }
  message.thinkDurationSeconds = Math.max(1, Math.round((performance.now() - message.thinkStartedAt) / 1000));
}

function escapeHtml(content) {
  return String(content || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function createStreamAnimator(message) {
  let pendingContent = '';
  let animationFrame = 0;
  let isFinishing = false;
  let finishResolver = null;
  const finishPromise = new Promise((resolve) => {
    finishResolver = resolve;
  });

  function scheduleFlush() {
    if (animationFrame) {
      return;
    }
    animationFrame = window.requestAnimationFrame(flushFrame);
  }

  function flushFrame() {
    animationFrame = 0;
    if (!pendingContent) {
      if (isFinishing) {
        finishResolver?.();
      }
      return;
    }

    const chunkSize = nextChunkSize(pendingContent.length);
    message.content += pendingContent.slice(0, chunkSize);
    pendingContent = pendingContent.slice(chunkSize);
    updateThinkDuration(message);
    void scrollToBottomIfNeeded();
    scheduleFlush();
  }

  return {
    push(content) {
      if (!content) {
        return;
      }
      pendingContent += content;
      scheduleFlush();
    },
    replace(content) {
      if (animationFrame) {
        window.cancelAnimationFrame(animationFrame);
        animationFrame = 0;
      }
      pendingContent = '';
      message.content = content;
      finishResolver?.();
    },
    finish() {
      isFinishing = true;
      if (!pendingContent) {
        finishResolver?.();
      } else {
        scheduleFlush();
      }
      return finishPromise;
    }
  };
}

function nextChunkSize(pendingLength) {
  if (pendingLength > 360) {
    return 42;
  }
  if (pendingLength > 160) {
    return 28;
  }
  if (pendingLength > 60) {
    return 16;
  }
  return 8;
}

async function loadConversations() {
  if (!isAuthenticated.value) {
    return;
  }

  isLoadingConversations.value = true;
  conversationError.value = '';
  try {
    const response = await fetchConversations();
    conversations.value = Array.isArray(response) ? response : [];
  } catch (error) {
    conversations.value = [];
    conversationError.value = error.message;
  } finally {
    isLoadingConversations.value = false;
  }
}

async function openConversation(targetConversationId) {
  if (!targetConversationId || isLoadingConversationDetail.value || targetConversationId === conversationId.value) {
    return false;
  }

  conversationOpenMenuId.value = '';
  const previousConversationId = conversationId.value;
  const previousSelectedModelId = selectedModelId.value;
  const previousMessages = [...messages.value];

  isLoadingConversationDetail.value = true;
  conversationError.value = '';
  try {
    const response = await fetchConversationDetail(targetConversationId);
    conversationId.value = response.conversationId;
    selectedModelId.value = response.modelId || selectedModelId.value;
    messages.value = response.messages.map((message, index) => ({
      id: `${response.conversationId}-${message.createdAt}-${index}`,
      role: message.role,
      content: message.content
    }));
    if (messages.value.length === 0) {
      messages.value = buildNewConversationMessages();
    }
    updateBrowserUrl(response.conversationId, { replace: false });
    await scrollToBottom();
    return true;
  } catch (error) {
    conversationId.value = previousConversationId;
    selectedModelId.value = previousSelectedModelId;
    messages.value = previousMessages;
    conversationError.value = error.message;
    return false;
  } finally {
    isLoadingConversationDetail.value = false;
  }
}

function resetConversationState() {
  conversationId.value = '';
  inputText.value = '';
  conversations.value = [];
  conversationError.value = '';
  updateBrowserUrl('', { replace: true });
}

function handlePopState() {
  if (!isAuthenticated.value) {
    return;
  }
  void syncConversationWithRoute({ replaceHistory: true });
}

async function syncConversationWithRoute({ replaceHistory }) {
  const routeConversationId = conversationIdFromLocation();
  if (!routeConversationId) {
    startNewChatForRoute(replaceHistory);
    return;
  }

  const opened = await openConversationFromRoute(routeConversationId, replaceHistory);
  if (!opened) {
    startNewChatForRoute(replaceHistory);
  }
}

async function openConversationFromRoute(targetConversationId, replaceHistory) {
  if (!targetConversationId) {
    return false;
  }

  const previousConversationId = conversationId.value;
  const previousSelectedModelId = selectedModelId.value;
  const previousMessages = [...messages.value];

  isLoadingConversationDetail.value = true;
  conversationError.value = '';
  try {
    const response = await fetchConversationDetail(targetConversationId);
    conversationId.value = response.conversationId;
    selectedModelId.value = response.modelId || selectedModelId.value;
    messages.value = response.messages.map((message, index) => ({
      id: `${response.conversationId}-${message.createdAt}-${index}`,
      role: message.role,
      content: message.content
    }));
    if (messages.value.length === 0) {
      messages.value = buildNewConversationMessages();
    }
    updateBrowserUrl(response.conversationId, { replace: replaceHistory });
    await scrollToBottom();
    return true;
  } catch (error) {
    conversationId.value = previousConversationId;
    selectedModelId.value = previousSelectedModelId;
    messages.value = previousMessages;
    conversationError.value = error.message;
    return false;
  } finally {
    isLoadingConversationDetail.value = false;
  }
}

function startNewChatForRoute(replaceHistory) {
  conversationId.value = '';
  inputText.value = '';
  conversationError.value = '';
  messages.value = buildNewConversationMessages();
  updateBrowserUrl('', { replace: replaceHistory });
}

function conversationIdFromLocation() {
  const pathname = window.location.pathname || '/';
  const matched = pathname.match(/^\/c\/([^/]+)$/);
  return matched ? decodeURIComponent(matched[1]) : '';
}

function updateBrowserUrl(targetConversationId, { replace }) {
  const targetPath = targetConversationId ? `/c/${encodeURIComponent(targetConversationId)}` : '/';
  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  if (currentPath === targetPath) {
    return;
  }

  const historyMethod = replace ? 'replaceState' : 'pushState';
  window.history[historyMethod]({}, '', targetPath);
}

function buildLoggedOutMessages(content = '你好，我是 yin-bo-agent 智能助手平台。先登录，然后我们继续聊技术和项目。') {
  return [
    {
      id: crypto.randomUUID(),
      role: 'assistant',
      content
    }
  ];
}

function buildNewConversationMessages() {
  return [];
}

async function scrollToBottom() {
  await nextTick();
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight;
    shouldAutoScroll.value = true;
  }
}

async function scrollToBottomIfNeeded() {
  if (!shouldAutoScroll.value) {
    return;
  }
  await scrollToBottom();
}

function handleMessageListScroll() {
  shouldAutoScroll.value = isMessageListNearBottom();
}

function isMessageListNearBottom() {
  if (!messageList.value) {
    return true;
  }
  const distanceToBottom = messageList.value.scrollHeight
    - messageList.value.scrollTop
    - messageList.value.clientHeight;
  return distanceToBottom < 96;
}

function handleAuthPointerMove(event) {
  const rect = event.currentTarget.getBoundingClientRect();
  pendingAuthPointer = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  };
  schedulePointerFrame();
}

function resetAuthPointer(event) {
  const targetRect = event?.currentTarget?.getBoundingClientRect?.();
  pendingAuthPointer = {
    x: targetRect ? targetRect.width / 2 : window.innerWidth / 2,
    y: targetRect ? targetRect.height / 2 : window.innerHeight / 2
  };
  schedulePointerFrame();
}

function schedulePointerFrame() {
  if (pointerAnimationFrame) {
    return;
  }
  pointerAnimationFrame = window.requestAnimationFrame(() => {
    authPointer.value = { ...pendingAuthPointer };
    pointerAnimationFrame = 0;
  });
}

function stopPointerFrame() {
  if (!pointerAnimationFrame) {
    return;
  }
  window.cancelAnimationFrame(pointerAnimationFrame);
  pointerAnimationFrame = 0;
}
</script>

<template>
  <main
    class="experience-page"
    :style="authPageStyle"
    @pointermove="handleAuthPointerMove"
    @pointerleave="resetAuthPointer"
  >
    <div class="honeycomb-field" aria-hidden="true">
      <div class="honeycomb-grid"></div>
      <div class="honeycomb-spotlight"></div>
    </div>

    <Transition name="shell-transition" mode="out-in">
      <section
      v-if="!isAuthenticated"
      key="auth"
      class="auth-page"
    >
      <section class="auth-window">
        <div class="auth-logo" aria-hidden="true">
          <svg class="yinbo-logo" viewBox="0 0 32 32">
            <circle cx="16" cy="16" r="2.2" />
            <path d="M16 5.5c4.4 0 7.7 2.6 7.7 6.2 0 2.8-2 4.8-5.6 5.7" />
            <path d="M25.1 20.6c-2.2 3.8-6.1 5.3-9.2 3.5-2.4-1.4-3.2-4.1-2.2-7.7" />
            <path d="M6.9 20.6c-2.2-3.8-1.6-7.9 1.5-9.7 2.4-1.4 5.2-.7 7.8 2" />
            <path d="M16 26.5c-4.4 0-7.7-2.6-7.7-6.2 0-2.8 2-4.8 5.6-5.7" />
            <path d="M6.9 11.4c2.2-3.8 6.1-5.3 9.2-3.5 2.4 1.4 3.2 4.1 2.2 7.7" />
            <path d="M25.1 11.4c2.2 3.8 1.6 7.9-1.5 9.7-2.4 1.4-5.2.7-7.8-2" />
          </svg>
        </div>
        <div class="auth-title">
          <span>yin-bo-agent</span>
        </div>

        <div class="auth-switch" :class="`is-${authMode}`" aria-label="认证方式">
          <span class="auth-switch-indicator" aria-hidden="true"></span>
          <button
            type="button"
            class="auth-switch-button"
            :class="{ active: authMode === 'login' }"
            @click="authMode = 'login'"
          >
            登录
          </button>
          <button
            type="button"
            class="auth-switch-button"
            :class="{ active: authMode === 'register' }"
            @click="authMode = 'register'"
          >
            注册
          </button>
        </div>

        <form class="auth-form" @submit.prevent="submitAuthForm">
          <label for="username">用户名</label>
          <input id="username" v-model="authForm.username" type="text" autocomplete="username" />

          <label for="password">密码</label>
          <div class="password-field">
            <input
              id="password"
              v-model="authForm.password"
              :type="isPasswordVisible ? 'text' : 'password'"
              :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'"
            />
            <button
              type="button"
              class="password-toggle"
              :class="{ visible: isPasswordVisible }"
              :aria-label="isPasswordVisible ? '隐藏密码' : '显示密码'"
              :title="isPasswordVisible ? '隐藏密码' : '显示密码'"
              @click="togglePasswordVisibility"
            >
              <svg
                class="password-icon password-icon-eye"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  d="M2.25 12s3.75-6.75 9.75-6.75S21.75 12 21.75 12s-3.75 6.75-9.75 6.75S2.25 12 2.25 12Z"
                />
                <circle cx="12" cy="12" r="3.25" />
              </svg>
              <svg
                class="password-icon password-icon-eye-off"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  d="M3 3l18 18"
                />
                <path
                  d="M10.58 5.14A10.76 10.76 0 0 1 12 5.25c6 0 9.75 6.75 9.75 6.75a18.72 18.72 0 0 1-4.1 4.94M6.61 6.61A19.13 19.13 0 0 0 2.25 12S6 18.75 12 18.75a10.9 10.9 0 0 0 3.42-.53M9.88 9.88A3 3 0 0 0 9 12a3 3 0 0 0 4.12 2.79"
                />
              </svg>
            </button>
          </div>

          <div class="auth-feedback" :class="{ 'has-error': Boolean(loginError) }">
            <p v-if="loginError" class="error-text">{{ loginError }}</p>
          </div>

          <button class="auth-submit" type="submit" :disabled="isAuthSubmitting">
            {{ authSubmitText }}
          </button>
        </form>
      </section>
    </section>

  <section v-else key="app" class="app-shell" :class="{ 'sidebar-collapsed': isSidebarCollapsed }">
    <nav v-if="isSidebarCollapsed" class="sidebar-rail" aria-label="快捷导航">
      <div class="rail-top">
        <button class="rail-button rail-logo logo-morph" type="button" data-tooltip="展开导航栏" @click="toggleSidebar">
          <span class="logo-text">
            <svg class="yinbo-logo" viewBox="0 0 32 32" aria-hidden="true">
              <circle cx="16" cy="16" r="2.2" />
              <path d="M16 5.5c4.4 0 7.7 2.6 7.7 6.2 0 2.8-2 4.8-5.6 5.7" />
              <path d="M25.1 20.6c-2.2 3.8-6.1 5.3-9.2 3.5-2.4-1.4-3.2-4.1-2.2-7.7" />
              <path d="M6.9 20.6c-2.2-3.8-1.6-7.9 1.5-9.7 2.4-1.4 5.2-.7 7.8 2" />
              <path d="M16 26.5c-4.4 0-7.7-2.6-7.7-6.2 0-2.8 2-4.8 5.6-5.7" />
              <path d="M6.9 11.4c2.2-3.8 6.1-5.3 9.2-3.5 2.4 1.4 3.2 4.1 2.2 7.7" />
              <path d="M25.1 11.4c2.2 3.8 1.6 7.9-1.5 9.7-2.4 1.4-5.2.7-7.8-2" />
            </svg>
          </span>
          <svg class="logo-hover-icon" viewBox="0 0 24 24" aria-hidden="true">
            <rect x="4" y="5" width="16" height="14" rx="3" />
            <path d="M9 5v14" />
          </svg>
        </button>
        <button class="rail-button" type="button" aria-label="新对话" data-tooltip="新对话" @click="startNewChatFromNavigation">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 5h7" />
            <path d="M19 5v7" />
            <path d="M18.5 13.5V18a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7.5a2 2 0 0 1 2-2h4.5" />
            <path d="M13 11l6-6" />
          </svg>
        </button>
        <button class="rail-button" type="button" aria-label="搜索聊天" data-tooltip="搜索聊天" @click="openSearch">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="M16 16l4 4" />
          </svg>
        </button>
        <button class="rail-button" type="button" aria-label="历史会话" data-tooltip="历史会话" @click="toggleRecentPopover">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M21 12a8.5 8.5 0 0 1-8.5 8.5H6l-3 2v-6.5A8.5 8.5 0 1 1 21 12Z" />
          </svg>
        </button>

        <div v-if="isRecentPopoverOpen" class="recent-popover">
          <strong>最近聊天</strong>
          <button
            v-for="conversation in conversations.slice(0, 10)"
            :key="conversation.conversationId"
            type="button"
            class="recent-popover-item"
            @click="openConversationFromPopover(conversation.conversationId)"
          >
            <span>{{ conversation.title }}</span>
            <small>{{ conversationMeta(conversation) }}</small>
          </button>
          <p v-if="conversations.length === 0" class="conversation-tip">还没有历史会话。</p>
        </div>
      </div>

      <button class="rail-user" type="button" aria-label="用户信息" data-tooltip="用户信息" @click="openUserMenuFromRail">
        {{ currentUser.displayName?.slice(0, 1) || '音' }}
      </button>
    </nav>

    <aside v-else class="sidebar">
      <div class="sidebar-main">
        <div class="brand">
          <div class="brand-mark">
            <svg class="yinbo-logo" viewBox="0 0 32 32" aria-hidden="true">
              <circle cx="16" cy="16" r="2.2" />
              <path d="M16 5.5c4.4 0 7.7 2.6 7.7 6.2 0 2.8-2 4.8-5.6 5.7" />
              <path d="M25.1 20.6c-2.2 3.8-6.1 5.3-9.2 3.5-2.4-1.4-3.2-4.1-2.2-7.7" />
              <path d="M6.9 20.6c-2.2-3.8-1.6-7.9 1.5-9.7 2.4-1.4 5.2-.7 7.8 2" />
              <path d="M16 26.5c-4.4 0-7.7-2.6-7.7-6.2 0-2.8 2-4.8 5.6-5.7" />
              <path d="M6.9 11.4c2.2-3.8 6.1-5.3 9.2-3.5 2.4 1.4 3.2 4.1 2.2 7.7" />
              <path d="M25.1 11.4c2.2 3.8 1.6 7.9-1.5 9.7-2.4 1.4-5.2.7-7.8-2" />
            </svg>
          </div>
          <div class="brand-copy">
            <h1>yin-bo-agent</h1>
            <p>智能助手平台</p>
          </div>
          <button class="sidebar-toggle" type="button" data-tooltip="隐藏导航栏" @click="toggleSidebar">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="4" y="5" width="16" height="14" rx="3" />
              <path d="M9 5v14" />
            </svg>
          </button>
        </div>

        <button class="sidebar-action-button" type="button" @click="startNewChatFromNavigation">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 5h7" />
            <path d="M19 5v7" />
            <path d="M18.5 13.5V18a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7.5a2 2 0 0 1 2-2h4.5" />
            <path d="M13 11l6-6" />
          </svg>
          <span>新对话</span>
        </button>

        <label class="sidebar-search">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="M16 16l4 4" />
          </svg>
          <input ref="searchInput" v-model="searchText" type="search" placeholder="搜索聊天" />
          <kbd>Ctrl K</kbd>
        </label>

        <section class="conversation-panel">
          <div class="conversation-panel-header">
            <strong>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M21 12a8.5 8.5 0 0 1-8.5 8.5H6l-3 2v-6.5A8.5 8.5 0 1 1 21 12Z" />
              </svg>
              历史会话
            </strong>
            <span v-if="isLoadingConversations">加载中...</span>
          </div>

          <p v-if="conversationError" class="conversation-tip conversation-error">{{ conversationError }}</p>
          <p v-else-if="conversations.length === 0 && !isLoadingConversations" class="conversation-tip">
            还没有历史会话，发一条消息试试看。
          </p>
          <p v-else-if="filteredConversations.length === 0 && !isLoadingConversations" class="conversation-tip">
            没有找到匹配的聊天。
          </p>

          <div v-else class="conversation-list">
            <article
              v-for="conversation in filteredConversations"
              :key="conversation.conversationId"
              class="conversation-item"
              :class="{ active: conversation.conversationId === conversationId }"
            >
              <button
                type="button"
                class="conversation-open"
                @click="openConversation(conversation.conversationId)"
              >
                <span class="conversation-title-line">
                  <strong>{{ conversation.title }}</strong>
                  <span
                    v-if="conversation.pinned"
                    class="conversation-pin-indicator"
                    title="已置顶"
                    aria-label="已置顶"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M14 4l6 6" />
                      <path d="M9.5 14.5L4 20" />
                      <path d="M14.5 3.5l-6 6 6 6 6-6-6-6Z" />
                    </svg>
                  </span>
                </span>
                <span>{{ conversationMeta(conversation) }}</span>
              </button>
              <div class="conversation-menu-host">
                <button
                  type="button"
                  class="conversation-more-button"
                  title="更多"
                  aria-label="更多会话操作"
                  :aria-expanded="conversationOpenMenuId === conversation.conversationId"
                  @click.stop="toggleConversationMenu(conversation.conversationId)"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="5" cy="12" r="1.4" />
                    <circle cx="12" cy="12" r="1.4" />
                    <circle cx="19" cy="12" r="1.4" />
                  </svg>
                </button>
                <div
                  v-if="conversationOpenMenuId === conversation.conversationId"
                  class="conversation-menu"
                  @click.stop
                >
                  <button type="button" class="conversation-menu-item" @click="toggleConversationPin(conversation)">
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M14 4l6 6" />
                      <path d="M9.5 14.5L4 20" />
                      <path d="M14.5 3.5l-6 6 6 6 6-6-6-6Z" />
                    </svg>
                    <span>{{ conversation.pinned ? '取消置顶' : '置顶聊天' }}</span>
                  </button>
                  <button type="button" class="conversation-menu-item danger" @click="openDeleteConversationDialog(conversation)">
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M3 6h18" />
                      <path d="M8 6V4h8v2" />
                      <path d="M19 6l-1 13a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
                      <path d="M10 11v6" />
                      <path d="M14 11v6" />
                    </svg>
                    <span>删除</span>
                  </button>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>

      <p v-if="loadError" class="muted">{{ loadError }}</p>

      <div class="user-menu" @click.stop>
        <button
          class="user-menu-trigger"
          type="button"
          :aria-expanded="isUserMenuOpen"
          @click="toggleUserMenu"
        >
          <span class="user-avatar">{{ currentUser.displayName?.slice(0, 1) || '音' }}</span>
          <span class="user-copy">
            <strong>{{ currentUser.displayName }}</strong>
            <span>ID：{{ currentUser.id }}</span>
          </span>
        </button>

        <div v-if="isUserMenuOpen" class="user-menu-layer">
          <div class="user-menu-popover">
            <button class="user-menu-action" type="button" @click="handleLogout">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M15 3h3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-3" />
                <path d="M10 17l5-5-5-5" />
                <path d="M15 12H4" />
              </svg>
              <span>退出登录</span>
            </button>
            <button class="user-menu-action danger" type="button" @click="openCancelMenu">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M3 6h18" />
                <path d="M8 6V4h8v2" />
                <path d="M19 6l-1 13a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
                <path d="M10 11v6" />
                <path d="M14 11v6" />
              </svg>
              <span>注销账号</span>
            </button>
          </div>

          <div v-if="isCancelMenuOpen" class="user-menu-submenu">
            <div class="user-menu-submenu-header">
              <strong>确认注销</strong>
              <button type="button" class="submenu-close" aria-label="关闭" @click="closeCancelMenu">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M6 6l12 12" />
                  <path d="M18 6 6 18" />
                </svg>
              </button>
            </div>
            <input
              id="cancel-password"
              v-model="cancelForm.password"
              class="sidebar-input"
              type="password"
              placeholder="输入密码确认注销"
              autocomplete="current-password"
            />
            <p v-if="cancelError" class="error-text">{{ cancelError }}</p>
            <button class="danger-button" type="button" @click="handleCancelAccount">
              {{ isCancellingAccount ? '注销中...' : '确认注销账号' }}
            </button>
          </div>
        </div>
      </div>
    </aside>

    <section class="chat" :class="{ 'chat-fresh': isFreshConversation }">
      <header class="chat-header">
        <div>
          <span>{{ activeConversationTitle }}</span>
          <strong>{{ selectedModel?.name }}</strong>
        </div>
        <small>
          {{
            isLoadingConversationDetail
              ? '历史消息加载中...'
              : conversationId || '新会话'
          }}
        </small>
      </header>

      <div v-if="isFreshConversation" class="chat-welcome">
        <h2>你好，{{ greetingName }}。准备好开始了吗</h2>
      </div>

      <div
        v-if="!isFreshConversation"
        ref="messageList"
        class="message-list"
        @scroll.passive="handleMessageListScroll"
      >
        <article
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="message.role"
        >
          <div class="message-bubble markdown-body" v-html="renderMessageContent(message)"></div>
        </article>

        <article v-if="isSending && !hasStreamingAssistant" class="message-row assistant">
          <div class="message-bubble thinking">正在思考</div>
        </article>
      </div>

      <form class="composer" @submit.prevent="submitMessage">
        <textarea
          v-model="inputText"
          placeholder="给 yin-bo-agent 发送消息"
          rows="1"
          @keydown.enter.exact.prevent="submitMessage"
        />
        <button
          class="think-toggle"
          :class="{ active: thinkMode }"
          type="button"
          :aria-pressed="thinkMode"
          :title="thinkMode ? '关闭 Think 模式' : '开启 Think 模式'"
          @click="toggleThinkMode"
        >
          <strong>Think</strong>
          <small>{{ thinkMode ? '已开启' : '深度' }}</small>
        </button>
        <div ref="modelMenu" class="composer-model-picker" :class="{ open: isModelMenuOpen }">
          <button
            class="composer-model-trigger"
            :class="{ active: isModelMenuOpen }"
            type="button"
            aria-haspopup="listbox"
            :aria-expanded="isModelMenuOpen"
            @click="toggleModelMenu"
          >
            <span class="composer-model-copy">
              <strong>{{ selectedModel?.name }}</strong>
              <small>{{ selectedModel?.provider || '默认供应商' }}</small>
            </span>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m7 10 5 5 5-5" />
            </svg>
          </button>

          <Transition name="model-menu">
            <div v-if="isModelMenuOpen" class="composer-model-menu" role="listbox" aria-label="模型列表">
              <section
                v-for="group in groupedModels"
                :key="group.provider"
                class="composer-model-group"
              >
                <header>{{ group.provider }}</header>
                <button
                  v-for="model in group.items"
                  :key="model.id"
                  type="button"
                  class="composer-model-option"
                  :class="{ selected: model.id === selectedModelId }"
                  @click="selectModel(model.id)"
                >
                  <strong>{{ model.name }}</strong>
                  <span>{{ model.id }}</span>
                </button>
              </section>
            </div>
          </Transition>
        </div>
        <button type="submit" :class="{ interrupting: isSending }" :disabled="!canUseComposer">
          {{ composerSubmitText }}
        </button>
      </form>
    </section>
  </section>
    </Transition>

    <Teleport to="body">
      <Transition name="delete-dialog">
        <div
          v-if="deleteDialog.open"
          class="delete-dialog-backdrop"
          @click.self="closeDeleteConversationDialog"
        >
          <section
            class="delete-dialog-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-dialog-title"
            aria-describedby="delete-dialog-description"
          >
            <button
              type="button"
              class="delete-dialog-close"
              aria-label="关闭删除确认"
              :disabled="isDeletingConversation"
              @click="closeDeleteConversationDialog"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12" />
                <path d="M18 6L6 18" />
              </svg>
            </button>

            <div class="delete-dialog-hero" aria-hidden="true">
              <span class="delete-dialog-icon">
                <svg viewBox="0 0 24 24">
                  <path d="M3 6h18" />
                  <path d="M8 6V4h8v2" />
                  <path d="M19 6l-1 13a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
                  <path d="M10 11v6" />
                  <path d="M14 11v6" />
                </svg>
              </span>
            </div>

            <div class="delete-dialog-copy">
              <p class="delete-dialog-eyebrow">危险操作</p>
              <h2 id="delete-dialog-title">永久删除这个会话？</h2>
              <p id="delete-dialog-description">
                会话和消息记录会被物理删除，删除后无法恢复。
              </p>
            </div>

            <div class="delete-dialog-target">
              <span>将被删除</span>
              <strong>{{ deleteDialogTitle }}</strong>
            </div>

            <p class="delete-dialog-warning">
              如果只是暂时不想看到它，可以先取消，后面我们再做归档会更稳。
            </p>

            <p v-if="deleteDialogError" class="delete-dialog-error">
              {{ deleteDialogError }}
            </p>

            <div class="delete-dialog-actions">
              <button
                type="button"
                class="delete-dialog-secondary"
                :disabled="isDeletingConversation"
                autofocus
                @click="closeDeleteConversationDialog"
              >
                先留着
              </button>
              <button
                type="button"
                class="delete-dialog-danger"
                :disabled="isDeletingConversation"
                @click="confirmDeleteConversation"
              >
                {{ isDeletingConversation ? '删除中...' : '永久删除' }}
              </button>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>
  </main>
</template>
