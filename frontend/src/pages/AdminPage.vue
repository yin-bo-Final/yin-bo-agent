<script setup>
import { computed, onMounted, ref } from 'vue';
import {
  createKnowledgeBase,
  deleteChunk,
  fetchAdminDashboard,
  fetchDocumentChunks,
  fetchKnowledgeBases,
  fetchKnowledgeDocuments,
  fetchKnowledgeOverview,
  ingestKnowledgeUrl,
  updateChunkEnabled,
  updateDocumentChunksEnabled,
  uploadKnowledgeDocument
} from '../api/adminApi';

const props = defineProps({
  currentUser: {
    type: Object,
    required: true
  }
});

const emit = defineEmits(['back-to-chat', 'logged-out', 'session-expired']);

const activeModule = ref('dashboard');
const dashboard = ref(null);
const overview = ref(null);
const knowledgeBases = ref([]);
const documents = ref([]);
const chunks = ref([]);
const selectedKnowledgeBaseId = ref('');
const selectedDocumentId = ref('');
const isLoadingDashboard = ref(false);
const isLoadingKnowledge = ref(false);
const isCreatingKnowledgeBase = ref(false);
const isIngesting = ref(false);
const adminError = ref('');
const createForm = ref({
  name: '',
  embeddingModel: 'Qwen/Qwen3-Embedding-8B',
  collectionName: ''
});
const ingestionMode = ref('upload');
const selectedFile = ref(null);
const urlForm = ref({
  url: '',
  fileName: ''
});
const ingestionOptions = ref({
  strategy: 'RECURSIVE',
  chunkSize: 1000,
  chunkOverlap: 150,
  maxChunks: 200
});

const selectedKnowledgeBase = computed(() => {
  return knowledgeBases.value.find((item) => item.knowledgeBaseId === selectedKnowledgeBaseId.value) || null;
});
const selectedDocument = computed(() => {
  return documents.value.find((item) => item.documentId === selectedDocumentId.value) || null;
});
const canCreateKnowledgeBase = computed(() => {
  return createForm.value.name.trim() && createForm.value.embeddingModel.trim() && createForm.value.collectionName.trim();
});
const canIngest = computed(() => {
  if (!selectedKnowledgeBaseId.value || isIngesting.value) {
    return false;
  }
  return ingestionMode.value === 'upload' ? Boolean(selectedFile.value) : Boolean(urlForm.value.url.trim());
});
const averageResponseStatus = computed(() => {
  const value = dashboard.value?.averageResponseTimeMs;
  if (value === null || value === undefined) {
    return 'pending';
  }
  if (value > 15000) {
    return 'danger';
  }
  if (value <= 10000) {
    return 'good';
  }
  return 'warn';
});

onMounted(async () => {
  await Promise.all([loadDashboard(), loadKnowledge()]);
});

async function loadDashboard() {
  isLoadingDashboard.value = true;
  try {
    dashboard.value = await fetchAdminDashboard();
  } catch (error) {
    handleAdminError(error);
  } finally {
    isLoadingDashboard.value = false;
  }
}

async function loadKnowledge() {
  isLoadingKnowledge.value = true;
  try {
    const [overviewResponse, basesResponse] = await Promise.all([
      fetchKnowledgeOverview(),
      fetchKnowledgeBases()
    ]);
    overview.value = overviewResponse;
    knowledgeBases.value = Array.isArray(basesResponse) ? basesResponse : [];
    if (overviewResponse?.embeddingModel) {
      createForm.value.embeddingModel = overviewResponse.embeddingModel;
    }
    if (!selectedKnowledgeBaseId.value && knowledgeBases.value.length > 0) {
      selectedKnowledgeBaseId.value = knowledgeBases.value[0].knowledgeBaseId;
    }
    if (selectedKnowledgeBaseId.value) {
      await loadDocuments(selectedKnowledgeBaseId.value);
    }
  } catch (error) {
    handleAdminError(error);
  } finally {
    isLoadingKnowledge.value = false;
  }
}

async function handleCreateKnowledgeBase() {
  if (!canCreateKnowledgeBase.value || isCreatingKnowledgeBase.value) {
    return;
  }
  isCreatingKnowledgeBase.value = true;
  adminError.value = '';
  try {
    const created = await createKnowledgeBase({
      name: createForm.value.name.trim(),
      embeddingModel: createForm.value.embeddingModel.trim(),
      collectionName: createForm.value.collectionName.trim()
    });
    createForm.value = {
      name: '',
      embeddingModel: 'Qwen/Qwen3-Embedding-8B',
      collectionName: ''
    };
    selectedKnowledgeBaseId.value = created.knowledgeBaseId;
    await loadKnowledge();
  } catch (error) {
    handleAdminError(error);
  } finally {
    isCreatingKnowledgeBase.value = false;
  }
}

async function selectKnowledgeBase(knowledgeBaseId) {
  if (selectedKnowledgeBaseId.value === knowledgeBaseId) {
    return;
  }
  selectedKnowledgeBaseId.value = knowledgeBaseId;
  selectedDocumentId.value = '';
  chunks.value = [];
  await loadDocuments(knowledgeBaseId);
}

async function loadDocuments(knowledgeBaseId) {
  documents.value = await fetchKnowledgeDocuments(knowledgeBaseId);
  if (selectedDocumentId.value) {
    const stillExists = documents.value.some((item) => item.documentId === selectedDocumentId.value);
    if (!stillExists) {
      selectedDocumentId.value = '';
      chunks.value = [];
    }
  }
}

function handleFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null;
}

async function submitIngestion() {
  if (!canIngest.value) {
    return;
  }
  isIngesting.value = true;
  adminError.value = '';
  try {
    const payload = normalizeIngestionPayload();
    if (ingestionMode.value === 'upload') {
      await uploadKnowledgeDocument(selectedKnowledgeBaseId.value, {
        ...payload,
        file: selectedFile.value
      });
      selectedFile.value = null;
    } else {
      await ingestKnowledgeUrl(selectedKnowledgeBaseId.value, {
        ...payload,
        url: urlForm.value.url.trim(),
        fileName: urlForm.value.fileName.trim()
      });
      urlForm.value = {
        url: '',
        fileName: ''
      };
    }
    await loadKnowledge();
  } catch (error) {
    handleAdminError(error);
  } finally {
    isIngesting.value = false;
  }
}

async function openDocument(documentId) {
  selectedDocumentId.value = documentId;
  adminError.value = '';
  try {
    chunks.value = await fetchDocumentChunks(documentId);
  } catch (error) {
    handleAdminError(error);
  }
}

async function setAllChunksEnabled(enabled) {
  if (!selectedDocumentId.value) {
    return;
  }
  try {
    chunks.value = await updateDocumentChunksEnabled(selectedDocumentId.value, enabled);
    await loadDocuments(selectedKnowledgeBaseId.value);
  } catch (error) {
    handleAdminError(error);
  }
}

async function toggleChunk(chunk) {
  try {
    const updated = await updateChunkEnabled(chunk.chunkId, !chunk.enabled);
    chunks.value = chunks.value.map((item) => item.chunkId === updated.chunkId ? updated : item);
  } catch (error) {
    handleAdminError(error);
  }
}

async function removeChunk(chunk) {
  try {
    await deleteChunk(chunk.chunkId);
    chunks.value = chunks.value.filter((item) => item.chunkId !== chunk.chunkId);
    await loadDocuments(selectedKnowledgeBaseId.value);
  } catch (error) {
    handleAdminError(error);
  }
}

function normalizeIngestionPayload() {
  const strategy = ingestionOptions.value.strategy;
  if (strategy === 'NONE') {
    return {
      strategy,
      maxChunks: 1
    };
  }
  return {
    strategy,
    chunkSize: Number(ingestionOptions.value.chunkSize || 1000),
    chunkOverlap: Number(ingestionOptions.value.chunkOverlap || 0),
    maxChunks: Number(ingestionOptions.value.maxChunks || 200)
  };
}

function handleAdminError(error) {
  if (error.message?.includes('未登录') || error.message?.includes('会话已过期')) {
    emit('session-expired');
    return;
  }
  adminError.value = error.message || '管理后台请求失败';
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString();
}

function formatBytes(value) {
  const bytes = Number(value || 0);
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '待生成';
}

function formatDuration(value) {
  if (value === null || value === undefined) {
    return '待接入';
  }
  if (value < 1000) {
    return `${value} ms`;
  }
  return `${(value / 1000).toFixed(2)} s`;
}

function metricText(value, fallback = '待接入') {
  return value === null || value === undefined ? fallback : formatNumber(value);
}
</script>

<template>
  <main class="admin-page">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <span class="admin-brand-mark">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 5h16" />
            <path d="M4 12h16" />
            <path d="M4 19h16" />
            <path d="M8 5v14" />
            <path d="M16 5v14" />
          </svg>
        </span>
        <div>
          <strong>管理后台</strong>
          <small>{{ props.currentUser.displayName }}</small>
        </div>
      </div>

      <nav class="admin-nav" aria-label="管理后台导航">
        <button type="button" :class="{ active: activeModule === 'dashboard' }" @click="activeModule = 'dashboard'">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 13h6V4H4v9Z" />
            <path d="M14 20h6V4h-6v16Z" />
            <path d="M4 20h6v-3H4v3Z" />
          </svg>
          <span>DashBoard</span>
        </button>
        <button type="button" :class="{ active: activeModule === 'knowledge' }" @click="activeModule = 'knowledge'">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 5.5A2.5 2.5 0 0 1 7.5 3H20v16H7.5A2.5 2.5 0 0 0 5 21.5v-16Z" />
            <path d="M5 5.5A2.5 2.5 0 0 0 2.5 3H2v16h.5A2.5 2.5 0 0 1 5 21.5" />
            <path d="M9 8h7" />
            <path d="M9 12h6" />
          </svg>
          <span>知识库管理</span>
        </button>
      </nav>

      <button class="admin-back-button" type="button" @click="emit('back-to-chat')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" />
          <path d="M9 12h12" />
        </svg>
        <span>返回会话</span>
      </button>
    </aside>

    <section class="admin-main">
      <header class="admin-header">
        <div>
          <span>{{ activeModule === 'dashboard' ? '系统指标' : '知识资产' }}</span>
          <h1>{{ activeModule === 'dashboard' ? 'DashBoard' : '知识库管理' }}</h1>
        </div>
        <button type="button" class="admin-refresh-button" @click="activeModule === 'dashboard' ? loadDashboard() : loadKnowledge()">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 6v5h-5" />
            <path d="M4 18v-5h5" />
            <path d="M19 11a7 7 0 0 0-12.2-4.2L4 9" />
            <path d="M5 13a7 7 0 0 0 12.2 4.2L20 15" />
          </svg>
          <span>刷新</span>
        </button>
      </header>

      <p v-if="adminError" class="admin-error">{{ adminError }}</p>

      <section v-if="activeModule === 'dashboard'" class="admin-section">
        <div class="metric-grid">
          <article class="metric-card">
            <span>活跃用户</span>
            <strong>{{ isLoadingDashboard ? '...' : metricText(dashboard?.activeUserCount) }}</strong>
            <small>最近 24 小时登录</small>
          </article>
          <article class="metric-card">
            <span>消息数</span>
            <strong>{{ isLoadingDashboard ? '...' : metricText(dashboard?.messageCount) }}</strong>
            <small>全量 chat_message</small>
          </article>
          <article class="metric-card">
            <span>会话数</span>
            <strong>{{ isLoadingDashboard ? '...' : metricText(dashboard?.conversationCount) }}</strong>
            <small>全量会话</small>
          </article>
          <article class="metric-card">
            <span>流量数</span>
            <strong>{{ isLoadingDashboard ? '...' : metricText(dashboard?.trafficCharacterCount) }}</strong>
            <small>消息字符量</small>
          </article>
          <article class="metric-card" :class="averageResponseStatus">
            <span>平均响应时间</span>
            <strong>{{ formatDuration(dashboard?.averageResponseTimeMs) }}</strong>
            <small>10 秒内良好，超过 15 秒标红</small>
          </article>
          <article class="metric-card muted">
            <span>知识错误率</span>
            <strong>待接入</strong>
            <small>RAG 评估完善后统计</small>
          </article>
          <article class="metric-card muted">
            <span>无知识率</span>
            <strong>待接入</strong>
            <small>RAG 检索链路完善后统计</small>
          </article>
        </div>
      </section>

      <section v-else class="admin-section knowledge-admin">
        <div class="knowledge-overview-grid">
          <article class="metric-card">
            <span>知识库个数</span>
            <strong>{{ metricText(overview?.knowledgeBaseCount) }}</strong>
          </article>
          <article class="metric-card">
            <span>总文档数</span>
            <strong>{{ metricText(overview?.totalDocumentCount) }}</strong>
          </article>
          <article class="metric-card">
            <span>含文档知识库</span>
            <strong>{{ metricText(overview?.knowledgeBaseWithDocumentsCount) }}</strong>
          </article>
        </div>

        <section class="admin-toolband">
          <form class="knowledge-create-form" @submit.prevent="handleCreateKnowledgeBase">
            <label>
              <span>知识库名称</span>
              <input v-model="createForm.name" type="text" placeholder="例如：Spring AI 学习资料" />
            </label>
            <label>
              <span>Embedding 模型</span>
              <input v-model="createForm.embeddingModel" type="text" readonly />
            </label>
            <label>
              <span>Collection 名称</span>
              <input v-model="createForm.collectionName" type="text" placeholder="spring_ai_docs" />
            </label>
            <button type="submit" :disabled="!canCreateKnowledgeBase || isCreatingKnowledgeBase">
              {{ isCreatingKnowledgeBase ? '创建中...' : '新建知识库' }}
            </button>
          </form>
        </section>

        <div class="knowledge-layout">
          <section class="knowledge-list-panel">
            <header>
              <strong>知识库</strong>
              <small>{{ isLoadingKnowledge ? '加载中...' : `${knowledgeBases.length} 个` }}</small>
            </header>
            <button
              v-for="base in knowledgeBases"
              :key="base.knowledgeBaseId"
              type="button"
              class="knowledge-base-item"
              :class="{ active: selectedKnowledgeBaseId === base.knowledgeBaseId }"
              @click="selectKnowledgeBase(base.knowledgeBaseId)"
            >
              <strong>{{ base.name }}</strong>
              <span>{{ base.collectionName }}</span>
              <small>{{ base.documentCount }} 文档 / {{ base.chunkCount }} 分块</small>
            </button>
            <p v-if="knowledgeBases.length === 0" class="admin-empty">还没有知识库。</p>
          </section>

          <section class="knowledge-workspace">
            <div v-if="selectedKnowledgeBase" class="ingestion-panel">
              <header>
                <div>
                  <strong>{{ selectedKnowledgeBase.name }}</strong>
                  <small>{{ selectedKnowledgeBase.embeddingModel }}</small>
                </div>
                <div class="ingestion-mode-tabs">
                  <button type="button" :class="{ active: ingestionMode === 'upload' }" @click="ingestionMode = 'upload'">本地上传</button>
                  <button type="button" :class="{ active: ingestionMode === 'url' }" @click="ingestionMode = 'url'">URL 解析</button>
                </div>
              </header>

              <form class="ingestion-form" @submit.prevent="submitIngestion">
                <label v-if="ingestionMode === 'upload'">
                  <span>选择文件</span>
                  <input type="file" accept=".pdf,.md,.txt,.doc,.docx" @change="handleFileChange" />
                </label>
                <template v-else>
                  <label>
                    <span>URL</span>
                    <input v-model="urlForm.url" type="url" placeholder="https://example.com/doc.pdf" />
                  </label>
                  <label>
                    <span>文件名</span>
                    <input v-model="urlForm.fileName" type="text" placeholder="可选" />
                  </label>
                </template>
                <label>
                  <span>分块策略</span>
                  <select v-model="ingestionOptions.strategy">
                    <option value="RECURSIVE">递归切块</option>
                    <option value="AUTO">自动策略</option>
                    <option value="NONE">不分块</option>
                  </select>
                </label>
                <label>
                  <span>分块大小</span>
                  <input v-model.number="ingestionOptions.chunkSize" type="number" min="100" :disabled="ingestionOptions.strategy === 'NONE'" />
                </label>
                <label>
                  <span>重叠大小</span>
                  <input v-model.number="ingestionOptions.chunkOverlap" type="number" min="0" :disabled="ingestionOptions.strategy === 'NONE'" />
                </label>
                <label>
                  <span>最大块数</span>
                  <input v-model.number="ingestionOptions.maxChunks" type="number" min="1" />
                </label>
                <button type="submit" :disabled="!canIngest">
                  {{ isIngesting ? '加工中...' : '开始入库' }}
                </button>
              </form>
            </div>

            <section class="document-panel">
              <header>
                <strong>文档</strong>
                <small>{{ documents.length }} 个</small>
              </header>
              <div class="document-table">
                <button
                  v-for="doc in documents"
                  :key="doc.documentId"
                  type="button"
                  class="document-row"
                  :class="{ active: selectedDocumentId === doc.documentId }"
                  @click="openDocument(doc.documentId)"
                >
                  <strong>{{ doc.fileName }}</strong>
                  <span>{{ formatBytes(doc.originalSizeBytes) }}</span>
                  <span>{{ doc.contentType || doc.sourceType }}</span>
                  <span>{{ formatDate(doc.updatedAt) }}</span>
                  <span>{{ formatDate(doc.textExtractedAt) }}</span>
                  <span>{{ formatDuration(doc.parseDurationMs) }}</span>
                  <span>{{ formatDuration(doc.chunkDurationMs) }}</span>
                  <span>{{ formatDuration(doc.embeddingDurationMs) }}</span>
                  <span>{{ formatDuration(doc.otherDurationMs) }}</span>
                  <span>{{ formatDuration(doc.totalDurationMs) }}</span>
                </button>
              </div>
              <p v-if="documents.length === 0" class="admin-empty">这个知识库还没有文档。</p>
            </section>

            <section class="chunk-panel">
              <header>
                <div>
                  <strong>分块列表</strong>
                  <small>{{ selectedDocument ? selectedDocument.fileName : '请选择文档' }}</small>
                </div>
                <div class="chunk-actions">
                  <button type="button" :disabled="!selectedDocumentId" @click="setAllChunksEnabled(true)">全量启用</button>
                  <button type="button" :disabled="!selectedDocumentId" @click="setAllChunksEnabled(false)">全量禁用</button>
                </div>
              </header>
              <article v-for="chunk in chunks" :key="chunk.chunkId" class="chunk-item" :class="{ disabled: !chunk.enabled }">
                <div class="chunk-item-header">
                  <div>
                    <strong>#{{ chunk.chunkIndex + 1 }} {{ chunk.title || '未命名分块' }}</strong>
                    <small>{{ chunk.tokenCount }} token / {{ chunk.charCount }} 字符 / {{ formatDate(chunk.updatedAt) }}</small>
                  </div>
                  <span class="chunk-status">{{ chunk.enabled ? '启用' : '禁用' }}</span>
                </div>
                <p>{{ chunk.content }}</p>
                <div class="chunk-item-actions">
                  <button type="button" @click="toggleChunk(chunk)">{{ chunk.enabled ? '禁用' : '启用' }}</button>
                  <button type="button" class="danger" @click="removeChunk(chunk)">删除</button>
                </div>
              </article>
              <p v-if="selectedDocumentId && chunks.length === 0" class="admin-empty">这个文档还没有分块。</p>
            </section>
          </section>
        </div>
      </section>
    </section>
  </main>
</template>
