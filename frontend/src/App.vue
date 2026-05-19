<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { cancelAccount, fetchCurrentUser, login, logout, register } from './api/authApi';
import {
  fetchConversationDetail,
  fetchConversations,
  fetchModels,
  sendChatMessage
} from './api/chatApi';

const fallbackModels = [
  {
    id: 'deepseek-ai/DeepSeek-V4-Flash',
    name: 'DeepSeek V4 Flash',
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
const cancelForm = ref({
  password: ''
});
const messageList = ref(null);
const authPointer = ref({
  x: 0,
  y: 0
});
let pendingAuthPointer = { x: 0, y: 0 };
let pointerAnimationFrame = 0;

const selectedModel = computed(() => {
  return models.value.find((model) => model.id === selectedModelId.value) || models.value[0];
});
const authPageStyle = computed(() => ({
  '--pointer-x': `${authPointer.value.x}px`,
  '--pointer-y': `${authPointer.value.y}px`
}));

const activeConversationTitle = computed(() => {
  const matchedConversation = conversations.value.find((item) => item.conversationId === conversationId.value);
  return matchedConversation?.title || '新会话';
});
const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !isSending.value && !isLoadingConversationDetail.value;
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

onMounted(async () => {
  const centerPoint = {
    x: window.innerWidth / 2,
    y: window.innerHeight / 2
  };
  pendingAuthPointer = centerPoint;
  authPointer.value = centerPoint;

  await restoreSession();

  try {
    const remoteModels = await fetchModels();
    if (Array.isArray(remoteModels) && remoteModels.length > 0) {
      const enabledModels = remoteModels.filter((model) => model.enabled !== false);
      models.value = enabledModels.length > 0 ? enabledModels : fallbackModels;
      const matchedModel = models.value.find((model) => model.id === selectedModelId.value);
      selectedModelId.value = matchedModel?.id || models.value[0]?.id || fallbackModels[0].id;
    }
  } catch (error) {
    loadError.value = '后端未启动时会使用前端内置模型列表。';
  }
});

onUnmounted(() => {
  stopPointerFrame();
});

async function restoreSession() {
  isCheckingLogin.value = true;
  try {
    const response = await fetchCurrentUser();
    currentUser.value = response.user;
    await loadConversations();
    startNewChat();
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
    startNewChat();
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
    await loadConversations();
    startNewChat();
  } catch (error) {
    loginError.value = error.message;
  } finally {
    isRegistering.value = false;
  }
}

async function submitMessage() {
  if (!isAuthenticated.value) {
    loginError.value = '请先登录，再发送消息。';
    return;
  }

  if (!canSend.value) {
    return;
  }

  const content = inputText.value.trim();
  inputText.value = '';
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'user',
    content
  });

  isSending.value = true;
  conversationError.value = '';
  await scrollToBottom();

  try {
    const response = await sendChatMessage({
      conversationId: conversationId.value,
      modelId: selectedModelId.value,
      messages: messages.value.map((message) => ({
        role: message.role,
        content: message.content
      }))
    });

    conversationId.value = response.conversationId;
    messages.value.push({
      id: crypto.randomUUID(),
      role: response.role,
      content: response.content
    });
    await loadConversations();
  } catch (error) {
    if (error.message.includes('未登录') || error.message.includes('会话已过期')) {
      currentUser.value = null;
      resetConversationState();
    }
    const assistantErrorMessage = error?.message?.trim()
      || '消息发送失败了。请确认你已经登录，并且后端、Redis、PostgreSQL 都已经启动。';
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'assistant',
      content: assistantErrorMessage
    });
  } finally {
    isSending.value = false;
    await scrollToBottom();
  }
}

function startNewChat() {
  conversationId.value = '';
  inputText.value = '';
  conversationError.value = '';
  messages.value = buildNewConversationMessages();
}

async function handleLogout() {
  try {
    await logout();
  } finally {
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
}

function buildLoggedOutMessages(content = '你好，我是音波AI agent。先登录，然后我们继续聊技术和项目。') {
  return [
    {
      id: crypto.randomUUID(),
      role: 'assistant',
      content
    }
  ];
}

function buildNewConversationMessages() {
  return [
    {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '新的对话已经开始。选择模型，然后告诉我你想做什么。'
    }
  ];
}

async function scrollToBottom() {
  await nextTick();
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight;
  }
}

function handleAuthPointerMove(event) {
  const rect = event.currentTarget.getBoundingClientRect();
  pendingAuthPointer = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  };
  schedulePointerFrame();
}

function resetAuthPointer() {
  pendingAuthPointer = {
    x: window.innerWidth / 2,
    y: window.innerHeight / 2
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
    v-if="!isAuthenticated"
    class="auth-page"
    :style="authPageStyle"
    @pointermove="handleAuthPointerMove"
    @pointerleave="resetAuthPointer"
  >
    <div class="honeycomb-field" aria-hidden="true">
      <div class="honeycomb-grid"></div>
      <div class="honeycomb-spotlight"></div>
    </div>

    <section class="auth-window">
      <div class="auth-title">
        <span>yin-bo-agent</span>
      </div>

      <div class="auth-switch" aria-label="认证方式">
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
        <input
          id="password"
          v-model="authForm.password"
          type="password"
          :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'"
        />

        <p v-if="authMode === 'register'" class="hint">密码长度需要至少 6 位</p>
        <p v-if="loginError" class="error-text">{{ loginError }}</p>

        <button class="auth-submit" type="submit" :disabled="isAuthSubmitting">
          {{ authSubmitText }}
        </button>
      </form>
    </section>
  </main>

  <main v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">音</div>
        <div>
          <h1>音波AI agent</h1>
          <p>智能助手平台</p>
        </div>
      </div>

      <button class="new-chat-button" type="button" @click="startNewChat">新对话</button>

      <section class="conversation-panel">
        <div class="conversation-panel-header">
          <strong>历史会话</strong>
          <span v-if="isLoadingConversations">加载中...</span>
        </div>

        <p v-if="conversationError" class="conversation-tip conversation-error">{{ conversationError }}</p>
        <p v-else-if="!isAuthenticated" class="conversation-tip">登录后可以查看自己的历史会话。</p>
        <p v-else-if="conversations.length === 0 && !isLoadingConversations" class="conversation-tip">
          还没有历史会话，发一条消息试试看。
        </p>

        <div v-else class="conversation-list">
          <button
            v-for="conversation in conversations"
            :key="conversation.conversationId"
            type="button"
            class="conversation-item"
            :class="{ active: conversation.conversationId === conversationId }"
            @click="openConversation(conversation.conversationId)"
          >
            <strong>{{ conversation.title }}</strong>
            <span>{{ conversation.lastMessageAt ? new Date(conversation.lastMessageAt).toLocaleString() : '刚刚' }}</span>
          </button>
        </div>
      </section>

      <section class="panel">
        <label for="model-select">模型</label>
        <select id="model-select" v-model="selectedModelId">
          <option v-for="model in models" :key="model.id" :value="model.id">
            {{ model.name }}
          </option>
        </select>
        <p>{{ selectedModel?.provider || 'provider' }}</p>
      </section>

      <section class="panel">
        <label>登录状态</label>
        <template v-if="isCheckingLogin">
          <p>正在恢复会话...</p>
        </template>
        <template v-else-if="isAuthenticated">
          <strong class="panel-title">{{ currentUser.displayName }}</strong>
          <p>用户名：{{ currentUser.username }}</p>
          <p>ID：{{ currentUser.id }}</p>
          <button class="ghost-button" type="button" @click="handleLogout">退出登录</button>
          <label for="cancel-password" class="danger-label">注销账号</label>
          <input
            id="cancel-password"
            v-model="cancelForm.password"
            class="sidebar-input"
            type="password"
            placeholder="再次输入密码确认"
            autocomplete="current-password"
          />
          <p v-if="cancelError" class="error-text">{{ cancelError }}</p>
          <button class="danger-button" type="button" @click="handleCancelAccount">
            {{ isCancellingAccount ? '注销中...' : '确认注销' }}
          </button>
        </template>
        <template v-else>
          <div class="auth-tabs">
            <button
              type="button"
              class="tab-button"
              :class="{ active: authMode === 'login' }"
              @click="authMode = 'login'"
            >
              登录
            </button>
            <button
              type="button"
              class="tab-button"
              :class="{ active: authMode === 'register' }"
              @click="authMode = 'register'"
            >
              注册
            </button>
          </div>
          <p>{{ authMode === 'login' ? '测试账号：admin / admin' : '用户名不可重复，注销后可重新注册' }}</p>
        </template>
      </section>

      <p v-if="loadError" class="muted">{{ loadError }}</p>
    </aside>

    <section class="chat">
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

      <div ref="messageList" class="message-list">
        <article
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="message.role"
        >
          <div class="avatar">{{ message.role === 'user' ? '你' : 'AI' }}</div>
          <div class="message-bubble">{{ message.content }}</div>
        </article>

        <article v-if="isSending" class="message-row assistant">
          <div class="avatar">AI</div>
          <div class="message-bubble thinking">正在思考</div>
        </article>
      </div>

      <form class="composer" @submit.prevent="submitMessage">
        <textarea
          v-model="inputText"
          placeholder="给音波AI agent 发送消息"
          rows="1"
          @keydown.enter.exact.prevent="submitMessage"
        />
        <button type="submit" :disabled="!canSend">发送</button>
      </form>
    </section>
  </main>
</template>
