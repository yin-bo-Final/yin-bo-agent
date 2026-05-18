<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { fetchCurrentUser, login, logout } from './api/authApi';
import { fetchModels, sendChatMessage } from './api/chatApi';

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
const messages = ref([
  {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: '你好，我是音波AI agent。先登录，然后我们继续聊技术和项目。'
  }
]);
const inputText = ref('');
const conversationId = ref('');
const isSending = ref(false);
const isCheckingLogin = ref(true);
const isLoggingIn = ref(false);
const loadError = ref('');
const loginError = ref('');
const authForm = ref({
  username: 'admin',
  password: 'admin'
});
const messageList = ref(null);

const selectedModel = computed(() => {
  return models.value.find((model) => model.id === selectedModelId.value) || models.value[0];
});

const canSend = computed(() => inputText.value.trim().length > 0 && !isSending.value);
const isAuthenticated = computed(() => currentUser.value !== null);

onMounted(async () => {
  await restoreSession();

  try {
    const remoteModels = await fetchModels();
    if (Array.isArray(remoteModels) && remoteModels.length > 0) {
      const enabledModels = remoteModels.filter((model) => model.enabled !== false);
      models.value = enabledModels.length > 0 ? enabledModels : fallbackModels;
      selectedModelId.value = models.value[0]?.id || fallbackModels[0].id;
    }
  } catch (error) {
    loadError.value = '后端未启动时会使用前端内置模型列表。';
  }
});

async function restoreSession() {
  isCheckingLogin.value = true;
  try {
    const response = await fetchCurrentUser();
    currentUser.value = response.user;
  } catch (_error) {
    currentUser.value = null;
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
    startNewChat();
  } catch (error) {
    loginError.value = error.message;
  } finally {
    isLoggingIn.value = false;
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
  } catch (error) {
    if (error.message.includes('未登录') || error.message.includes('会话已过期')) {
      currentUser.value = null;
    }
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '消息发送失败了。请确认你已经登录，并且后端、Redis、PostgreSQL 都已经启动。'
    });
  } finally {
    isSending.value = false;
    await scrollToBottom();
  }
}

function startNewChat() {
  conversationId.value = '';
  inputText.value = '';
  messages.value = [
    {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '新的对话已经开始。选择模型，然后告诉我你想做什么。'
    }
  ];
}

async function handleLogout() {
  try {
    await logout();
  } finally {
    currentUser.value = null;
    conversationId.value = '';
    inputText.value = '';
    loginError.value = '';
    messages.value = [
      {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '你已经退出登录。重新登录后可以继续使用对话功能。'
      }
    ];
  }
}

async function scrollToBottom() {
  await nextTick();
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight;
  }
}
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">音</div>
        <div>
          <h1>音波AI agent</h1>
          <p>智能助手平台</p>
        </div>
      </div>

      <button class="new-chat-button" type="button" @click="startNewChat">新对话</button>

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
        </template>
        <template v-else>
          <p>测试账号：admin / admin</p>
        </template>
      </section>

      <p v-if="loadError" class="muted">{{ loadError }}</p>
    </aside>

    <section class="chat">
      <header class="chat-header">
        <div>
          <span>{{ isAuthenticated ? '当前模型' : '登录账号' }}</span>
          <strong>{{ isAuthenticated ? selectedModel?.name : 'admin / admin' }}</strong>
        </div>
        <small>{{ isAuthenticated ? conversationId || '新会话' : '会话式登录已启用' }}</small>
      </header>

      <template v-if="isAuthenticated">
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
      </template>

      <section v-else class="login-shell">
        <form class="login-panel" @submit.prevent="submitLogin">
          <div class="login-copy">
            <h2>登录系统</h2>
            <p>先用测试账号进来，把登录链路跑通。后面我们再继续补注册、权限和会话管理。</p>
          </div>

          <label for="username">用户名</label>
          <input id="username" v-model="authForm.username" type="text" autocomplete="username" />

          <label for="password">密码</label>
          <input
            id="password"
            v-model="authForm.password"
            type="password"
            autocomplete="current-password"
          />

          <p class="hint">默认测试账号：admin / admin</p>
          <p v-if="loginError" class="error-text">{{ loginError }}</p>

          <button class="login-button" type="submit" :disabled="isLoggingIn">
            {{ isLoggingIn ? '登录中...' : '登录' }}
          </button>
        </form>
      </section>
    </section>
  </main>
</template>
