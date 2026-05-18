<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
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
const messages = ref([
  {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: '你好，我是音波AI agent。先选一个模型，然后直接开始对话。'
  }
]);
const inputText = ref('');
const conversationId = ref('');
const isSending = ref(false);
const loadError = ref('');
const messageList = ref(null);

const selectedModel = computed(() => {
  return models.value.find((model) => model.id === selectedModelId.value) || models.value[0];
});

const canSend = computed(() => inputText.value.trim().length > 0 && !isSending.value);

onMounted(async () => {
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

async function submitMessage() {
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
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '后端服务还没有连接成功。你可以先继续调前端页面，等后端启动后这里会返回真实接口响应。'
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

      <p v-if="loadError" class="muted">{{ loadError }}</p>
    </aside>

    <section class="chat">
      <header class="chat-header">
        <div>
          <span>当前模型</span>
          <strong>{{ selectedModel?.name }}</strong>
        </div>
        <small>{{ conversationId || '新会话' }}</small>
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
