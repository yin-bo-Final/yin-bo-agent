<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import {
  createKnowledgeBase,
  deleteChunk,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  fetchAdminDashboard,
  fetchDocumentChunks,
  fetchKnowledgeBase,
  fetchKnowledgeBases,
  fetchKnowledgeDocument,
  fetchKnowledgeDocuments,
  fetchKnowledgeOverview,
  ingestKnowledgeUrl,
  rebuildDocumentVectors,
  rechunkKnowledgeDocument,
  updateChunk,
  updateChunkEnabled,
  updateDocumentChunksEnabled,
  updateKnowledgeBase,
  uploadKnowledgeDocument
} from '../api/adminApi';

const props = defineProps({
  currentUser: {
    type: Object,
    required: true
  }
});

const emit = defineEmits(['back-to-chat', 'logged-out', 'session-expired']);

const route = ref(parseAdminRoute());
const dashboard = ref(null);
const overview = ref(null);
const knowledgeBases = ref([]);
const documents = ref([]);
const chunks = ref([]);
const selectedKnowledgeBase = ref(null);
const selectedDocument = ref(null);
const selectedChunkIds = ref(new Set());
const isLoadingDashboard = ref(false);
const isLoadingKnowledge = ref(false);
const isCreatingKnowledgeBase = ref(false);
const isIngesting = ref(false);
const isRechunking = ref(false);
const isRebuildingVectors = ref(false);
const isDeletingKnowledgeBase = ref(false);
const isDeletingDocument = ref(false);
const isDeletingChunk = ref(false);
const isUpdatingChunk = ref(false);
const isRefreshing = ref(false);
const isAdminSidebarCollapsed = ref(false);
const adminError = ref('');
const createFormError = ref('');
const ingestionFormError = ref('');
const rechunkFormError = ref('');
const baseKeyword = ref('');
const documentKeyword = ref('');
const documentStatusFilter = ref('ALL');
const chunkStatusFilter = ref('ALL');
const isCreateModalOpen = ref(false);
const isEditModalOpen = ref(false);
const detailKnowledgeBase = ref(null);
const deleteKnowledgeBaseDialog = ref({
  open: false,
  knowledgeBase: null
});
const deleteKnowledgeBaseError = ref('');
const deleteDocumentDialog = ref({
  open: false,
  document: null
});
const deleteDocumentError = ref('');
const deleteChunkDialog = ref({
  open: false,
  chunk: null
});
const deleteChunkError = ref('');
const isIngestionModalOpen = ref(false);
const isRechunkModalOpen = ref(false);
const detailDocument = ref(null);
const detailChunk = ref(null);
const viewingChunk = ref(null);
const editingChunk = ref(null);
const editChunkForm = ref({
  content: ''
});
const editChunkError = ref('');
const isChunkUpdateNoticeOpen = ref(false);
const isChunkStatusMenuOpen = ref(false);
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
const ingestionOptions = ref(defaultChunkOptions());
const rechunkForm = ref(defaultChunkOptions());
const rechunkDocument = ref(null);
const editKnowledgeBase = ref(null);
const editForm = ref({
  name: ''
});

const activeModule = computed(() => route.value.module);
const currentView = computed(() => route.value.view);
const currentHeader = computed(() => {
  if (activeModule.value === 'dashboard') {
    return { label: '系统指标', title: 'DashBoard', icon: 'dashboard' };
  }
  if (currentView.value === 'documents') {
    return { label: '文档资产', title: '文档管理', icon: 'documents' };
  }
  if (currentView.value === 'chunks') {
    return { label: '切片资产', title: '分块管理', icon: 'chunks' };
  }
  return { label: '知识资产', title: '知识库管理', icon: 'knowledge' };
});
const selectedKnowledgeBaseId = computed(() => route.value.knowledgeBaseId || '');
const selectedDocumentId = computed(() => route.value.documentId || '');
const canCreateKnowledgeBase = computed(() => {
  return createForm.value.name.trim() && createForm.value.embeddingModel.trim() && createForm.value.collectionName.trim();
});
const canIngest = computed(() => {
  if (!selectedKnowledgeBaseId.value || isIngesting.value) {
    return false;
  }
  return ingestionMode.value === 'upload' ? Boolean(selectedFile.value) : Boolean(urlForm.value.url.trim());
});

function toggleAdminSidebar() {
  isAdminSidebarCollapsed.value = !isAdminSidebarCollapsed.value;
}

function showOverflowTooltip(event, value) {
  const target = event.currentTarget;
  const text = value == null ? '' : String(value);
  const content = target.querySelector('.kc-tooltip-content') || target.querySelector('span') || target;
  const isOverflowing = content.scrollWidth > content.clientWidth + 1 || content.scrollHeight > content.clientHeight + 1;

  if (text && isOverflowing) {
    target.dataset.tooltip = text;
  } else {
    delete target.dataset.tooltip;
  }
}

function clearOverflowTooltip(event) {
  delete event.currentTarget.dataset.tooltip;
}

function statusFilterText(value) {
  if (value === 'ENABLED') {
    return '启用';
  }
  if (value === 'DISABLED') {
    return '禁用';
  }
  return '全部状态';
}

function setChunkStatus(value) {
  chunkStatusFilter.value = value;
  isChunkStatusMenuOpen.value = false;
}

function openCreateKnowledgeBaseModal() {
  createFormError.value = '';
  adminError.value = '';
  isCreateModalOpen.value = true;
}

function closeCreateKnowledgeBaseModal() {
  createFormError.value = '';
  isCreateModalOpen.value = false;
}
const filteredKnowledgeBases = computed(() => {
  const keyword = baseKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return knowledgeBases.value;
  }
  return knowledgeBases.value.filter((base) => {
    return [base.name, base.embeddingModel, base.collectionName]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(keyword));
  });
});
const filteredDocuments = computed(() => {
  const keyword = documentKeyword.value.trim().toLowerCase();
  return documents.value.filter((doc) => {
    const keywordMatched = !keyword || [doc.fileName, doc.sourceType, doc.contentType]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
    const statusMatched = documentStatusFilter.value === 'ALL' || doc.status === documentStatusFilter.value;
    return keywordMatched && statusMatched;
  });
});
const filteredChunks = computed(() => {
  if (chunkStatusFilter.value === 'ENABLED') {
    return chunks.value.filter((chunk) => chunk.enabled);
  }
  if (chunkStatusFilter.value === 'DISABLED') {
    return chunks.value.filter((chunk) => !chunk.enabled);
  }
  return chunks.value;
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
  window.addEventListener('popstate', handleRouteChange);
  await Promise.all([loadDashboard(), loadKnowledge()]);
});

onUnmounted(() => {
  window.removeEventListener('popstate', handleRouteChange);
});

function parseAdminRoute() {
  const segments = window.location.pathname.split('/').filter(Boolean);
  if (segments[0] !== 'admin') {
    return { module: 'dashboard', view: 'dashboard' };
  }
  if (segments[1] !== 'knowledge') {
    return { module: 'dashboard', view: 'dashboard' };
  }
  if (segments[2] && segments[3] === 'docs' && segments[4]) {
    return {
      module: 'knowledge',
      view: 'chunks',
      knowledgeBaseId: decodeURIComponent(segments[2]),
      documentId: decodeURIComponent(segments[4])
    };
  }
  if (segments[2]) {
    return {
      module: 'knowledge',
      view: 'documents',
      knowledgeBaseId: decodeURIComponent(segments[2])
    };
  }
  return { module: 'knowledge', view: 'bases' };
}

async function handleRouteChange() {
  route.value = parseAdminRoute();
  adminError.value = '';
  if (activeModule.value === 'knowledge') {
    await hydrateKnowledgeRoute();
  }
}

async function navigateTo(path) {
  if (window.location.pathname !== path) {
    window.history.pushState({}, '', path);
  }
  await handleRouteChange();
}

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
    await hydrateKnowledgeRoute();
  } catch (error) {
    handleAdminError(error);
  } finally {
    isLoadingKnowledge.value = false;
  }
}

async function hydrateKnowledgeRoute() {
  if (currentView.value === 'bases') {
    documents.value = [];
    chunks.value = [];
    selectedKnowledgeBase.value = null;
    selectedDocument.value = null;
    selectedChunkIds.value = new Set();
    return;
  }

  const baseId = selectedKnowledgeBaseId.value;
  if (!baseId) {
    return;
  }
  selectedKnowledgeBase.value = knowledgeBases.value.find((base) => base.knowledgeBaseId === baseId) || await fetchKnowledgeBase(baseId);
  documents.value = await fetchKnowledgeDocuments(baseId);

  if (currentView.value === 'chunks' && selectedDocumentId.value) {
    selectedDocument.value = documents.value.find((doc) => doc.documentId === selectedDocumentId.value)
      || await fetchKnowledgeDocument(selectedDocumentId.value);
    chunks.value = await fetchDocumentChunks(selectedDocumentId.value);
    selectedChunkIds.value = new Set();
  } else {
    selectedDocument.value = null;
    chunks.value = [];
    selectedChunkIds.value = new Set();
  }
}

async function refreshCurrentView() {
  if (isRefreshing.value) {
    return;
  }
  isRefreshing.value = true;
  try {
    if (activeModule.value === 'dashboard') {
      await loadDashboard();
      return;
    }
    await loadKnowledge();
  } finally {
    window.setTimeout(() => {
      isRefreshing.value = false;
    }, 260);
  }
}

async function handleCreateKnowledgeBase() {
  if (!canCreateKnowledgeBase.value || isCreatingKnowledgeBase.value) {
    return;
  }
  isCreatingKnowledgeBase.value = true;
  createFormError.value = '';
  try {
    await createKnowledgeBase({
      name: createForm.value.name.trim(),
      embeddingModel: createForm.value.embeddingModel.trim(),
      collectionName: createForm.value.collectionName.trim()
    });
    createForm.value = {
      name: '',
      embeddingModel: overview.value?.embeddingModel || 'Qwen/Qwen3-Embedding-8B',
      collectionName: ''
    };
    closeCreateKnowledgeBaseModal();
    await loadKnowledge();
    if (window.location.pathname !== '/admin/knowledge') {
      await navigateTo('/admin/knowledge');
    }
  } catch (error) {
    if (isSessionError(error)) {
      emit('session-expired');
      return;
    }
    createFormError.value = error.message || '知识库创建失败';
  } finally {
    isCreatingKnowledgeBase.value = false;
  }
}

async function removeKnowledgeBase(base) {
  adminError.value = '';
  deleteKnowledgeBaseError.value = '';
  deleteKnowledgeBaseDialog.value = {
    open: true,
    knowledgeBase: base
  };
}

function closeDeleteKnowledgeBaseDialog() {
  if (isDeletingKnowledgeBase.value) {
    return;
  }
  deleteKnowledgeBaseDialog.value = {
    open: false,
    knowledgeBase: null
  };
  deleteKnowledgeBaseError.value = '';
}

async function confirmDeleteKnowledgeBase() {
  const target = deleteKnowledgeBaseDialog.value.knowledgeBase;
  if (!target?.knowledgeBaseId || isDeletingKnowledgeBase.value) {
    return;
  }
  isDeletingKnowledgeBase.value = true;
  deleteKnowledgeBaseError.value = '';
  try {
    adminError.value = '';
    await deleteKnowledgeBase(target.knowledgeBaseId);
    deleteKnowledgeBaseDialog.value = {
      open: false,
      knowledgeBase: null
    };
    await loadKnowledge();
    await navigateTo('/admin/knowledge');
  } catch (error) {
    if (isSessionError(error)) {
      emit('session-expired');
      return;
    }
    deleteKnowledgeBaseError.value = error.message || '知识库删除失败';
  } finally {
    isDeletingKnowledgeBase.value = false;
  }
}

function openEditKnowledgeBaseModal(base) {
  editKnowledgeBase.value = base;
  editForm.value = {
    name: base.name
  };
  isEditModalOpen.value = true;
}

async function submitEditKnowledgeBase() {
  if (!editKnowledgeBase.value || !editForm.value.name.trim()) {
    return;
  }
  try {
    await updateKnowledgeBase(editKnowledgeBase.value.knowledgeBaseId, {
      name: editForm.value.name.trim()
    });
    isEditModalOpen.value = false;
    await loadKnowledge();
  } catch (error) {
    handleAdminError(error);
  }
}

async function openDocuments(base) {
  await navigateTo(`/admin/knowledge/${base.knowledgeBaseId}`);
}

function openIngestionModal() {
  adminError.value = '';
  ingestionFormError.value = '';
  selectedFile.value = null;
  urlForm.value = { url: '', fileName: '' };
  ingestionOptions.value = defaultChunkOptions();
  ingestionMode.value = 'upload';
  isIngestionModalOpen.value = true;
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
  ingestionFormError.value = '';
  try {
    const payload = normalizeChunkPayload(ingestionOptions.value);
    if (ingestionMode.value === 'upload') {
      await uploadKnowledgeDocument(selectedKnowledgeBaseId.value, {
        ...payload,
        file: selectedFile.value
      });
    } else {
      await ingestKnowledgeUrl(selectedKnowledgeBaseId.value, {
        ...payload,
        url: urlForm.value.url.trim(),
        fileName: urlForm.value.fileName.trim()
      });
    }
    isIngestionModalOpen.value = false;
    await loadKnowledge();
  } catch (error) {
    if (isSessionError(error)) {
      emit('session-expired');
      return;
    }
    adminError.value = '';
    ingestionFormError.value = error.message || '文档入库失败';
  } finally {
    isIngesting.value = false;
  }
}

async function openDocumentChunks(document) {
  await navigateTo(`/admin/knowledge/${selectedKnowledgeBaseId.value}/docs/${document.documentId}`);
}

function openRechunkModal(targetDocument) {
  adminError.value = '';
  rechunkFormError.value = '';
  rechunkDocument.value = targetDocument;
  rechunkForm.value = {
    strategy: targetDocument.chunkStrategy || 'RECURSIVE',
    chunkSize: targetDocument.chunkSize || 1000,
    chunkOverlap: targetDocument.chunkOverlap || 150,
    maxChunks: targetDocument.maxChunks || 200
  };
  isRechunkModalOpen.value = true;
}

function closeRechunkModal() {
  if (isRechunking.value) {
    return;
  }
  rechunkFormError.value = '';
  isRechunkModalOpen.value = false;
}

async function submitRechunk() {
  if (!rechunkDocument.value || isRechunking.value) {
    return;
  }
  isRechunking.value = true;
  adminError.value = '';
  rechunkFormError.value = '';
  try {
    await rechunkKnowledgeDocument(rechunkDocument.value.documentId, normalizeChunkPayload(rechunkForm.value));
    rechunkFormError.value = '';
    isRechunkModalOpen.value = false;
    await loadKnowledge();
  } catch (error) {
    if (isSessionError(error)) {
      emit('session-expired');
      return;
    }
    adminError.value = '';
    rechunkFormError.value = error.message || '文档重新分块失败';
  } finally {
    isRechunking.value = false;
  }
}

function removeDocument(document) {
  adminError.value = '';
  deleteDocumentError.value = '';
  deleteDocumentDialog.value = {
    open: true,
    document
  };
}

function closeDeleteDocumentDialog() {
  if (isDeletingDocument.value) {
    return;
  }
  deleteDocumentDialog.value = {
    open: false,
    document: null
  };
  deleteDocumentError.value = '';
}

async function confirmDeleteDocument() {
  const target = deleteDocumentDialog.value.document;
  if (!target?.documentId || isDeletingDocument.value) {
    return;
  }
  isDeletingDocument.value = true;
  deleteDocumentError.value = '';
  try {
    await deleteKnowledgeDocument(target.documentId);
    deleteDocumentDialog.value = {
      open: false,
      document: null
    };
    await loadKnowledge();
    if (selectedDocumentId.value === target.documentId) {
      await navigateTo(`/admin/knowledge/${selectedKnowledgeBaseId.value}`);
    }
  } catch (error) {
    if (isSessionError(error)) {
      emit('session-expired');
      return;
    }
    deleteDocumentError.value = error.message || '文档删除失败';
  } finally {
    isDeletingDocument.value = false;
  }
}

async function rebuildVectors() {
  if (!selectedDocumentId.value || isRebuildingVectors.value) {
    return;
  }
  isRebuildingVectors.value = true;
  try {
    selectedDocument.value = await rebuildDocumentVectors(selectedDocumentId.value);
    await hydrateKnowledgeRoute();
  } catch (error) {
    handleAdminError(error);
  } finally {
    isRebuildingVectors.value = false;
  }
}

async function setAllChunksEnabled(enabled) {
  if (!selectedDocumentId.value) {
    return;
  }
  try {
    chunks.value = await updateDocumentChunksEnabled(selectedDocumentId.value, enabled);
    selectedChunkIds.value = new Set();
    await loadKnowledge();
  } catch (error) {
    handleAdminError(error);
  }
}

async function setSelectedChunksEnabled(enabled) {
  const ids = Array.from(selectedChunkIds.value);
  if (ids.length === 0) {
    return;
  }
  try {
    const updatedChunks = await Promise.all(ids.map((chunkId) => updateChunkEnabled(chunkId, enabled)));
    const updatedMap = new Map(updatedChunks.map((chunk) => [chunk.chunkId, chunk]));
    chunks.value = chunks.value.map((chunk) => updatedMap.get(chunk.chunkId) || chunk);
    selectedChunkIds.value = new Set();
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

function removeChunk(chunk) {
  adminError.value = '';
  deleteChunkError.value = '';
  deleteChunkDialog.value = {
    open: true,
    chunk
  };
}

function closeDeleteChunkDialog() {
  if (isDeletingChunk.value) {
    return;
  }
  deleteChunkDialog.value = {
    open: false,
    chunk: null
  };
  deleteChunkError.value = '';
}

async function confirmDeleteChunk() {
  const target = deleteChunkDialog.value.chunk;
  if (!target?.chunkId || isDeletingChunk.value) {
    return;
  }
  isDeletingChunk.value = true;
  deleteChunkError.value = '';
  try {
    await deleteChunk(target.chunkId);
    deleteChunkDialog.value = {
      open: false,
      chunk: null
    };
    chunks.value = chunks.value.filter((item) => item.chunkId !== target.chunkId);
    await loadKnowledge();
  } catch (error) {
    if (isSessionError(error)) {
      emit('session-expired');
      return;
    }
    deleteChunkError.value = error.message || '分块删除失败';
  } finally {
    isDeletingChunk.value = false;
  }
}

function openEditChunkModal(chunk) {
  editingChunk.value = chunk;
  editChunkForm.value = {
    content: chunk.content || ''
  };
  editChunkError.value = '';
}

function closeEditChunkModal() {
  if (isUpdatingChunk.value) {
    return;
  }
  editingChunk.value = null;
  editChunkForm.value = {
    content: ''
  };
  editChunkError.value = '';
}

async function submitEditChunk() {
  if (!editingChunk.value?.chunkId || isUpdatingChunk.value) {
    return;
  }
  if (!editChunkForm.value.content.trim()) {
    editChunkError.value = '分块内容不能为空';
    return;
  }
  isUpdatingChunk.value = true;
  editChunkError.value = '';
  try {
    const updatedChunk = await updateChunk(editingChunk.value.chunkId, {
      content: editChunkForm.value.content
    });
    chunks.value = chunks.value.map((chunk) => (chunk.chunkId === updatedChunk.chunkId ? updatedChunk : chunk));
    editingChunk.value = null;
    editChunkForm.value = {
      content: ''
    };
    isChunkUpdateNoticeOpen.value = true;
    await loadKnowledge();
  } catch (error) {
    if (isSessionError(error)) {
      emit('session-expired');
      return;
    }
    editChunkError.value = error.message || '分块修改失败';
  } finally {
    isUpdatingChunk.value = false;
  }
}

function toggleChunkSelected(chunkId) {
  const next = new Set(selectedChunkIds.value);
  if (next.has(chunkId)) {
    next.delete(chunkId);
  } else {
    next.add(chunkId);
  }
  selectedChunkIds.value = next;
}

function toggleAllVisibleChunks() {
  if (filteredChunks.value.length === 0) {
    return;
  }
  const visibleIds = filteredChunks.value.map((chunk) => chunk.chunkId);
  const allSelected = visibleIds.every((id) => selectedChunkIds.value.has(id));
  const next = new Set(selectedChunkIds.value);
  visibleIds.forEach((id) => {
    if (allSelected) {
      next.delete(id);
    } else {
      next.add(id);
    }
  });
  selectedChunkIds.value = next;
}

function defaultChunkOptions() {
  return {
    strategy: 'RECURSIVE',
    chunkSize: 1000,
    chunkOverlap: 150,
    maxChunks: 200
  };
}

function normalizeChunkPayload(options) {
  const strategy = options.strategy || 'RECURSIVE';
  if (strategy === 'NONE') {
    return { strategy };
  }
  if (strategy === 'AUTO') {
    return { strategy };
  }
  return {
    strategy,
    chunkSize: Number(options.chunkSize || 1000),
    chunkOverlap: Number(options.chunkOverlap || 0),
    maxChunks: Number(options.maxChunks || 200)
  };
}

function handleAdminError(error) {
  if (isSessionError(error)) {
    emit('session-expired');
    return;
  }
  adminError.value = error.message || '后台管理请求失败';
}

function isSessionError(error) {
  return error.message?.includes('未登录') || error.message?.includes('会话已过期');
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
    return `${value}ms`;
  }
  return `${(value / 1000).toFixed(2)}s`;
}

function metricText(value, fallback = '待接入') {
  return value === null || value === undefined ? fallback : formatNumber(value);
}

function sourceText(document) {
  return document.sourceType === 'URL' ? 'URL' : 'Local File';
}

function typeText(document) {
  const type = document.contentType || document.fileName?.split('.').pop() || document.sourceType;
  return String(type).replace('application/', '').replace('text/', '');
}

function statusClass(status) {
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'danger';
  }
  return 'pending';
}

function statusText(status) {
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'failed';
  }
  return 'processing';
}
</script>

<template>
  <main class="admin-page" :class="{ 'admin-sidebar-collapsed': isAdminSidebarCollapsed }">
    <nav v-if="isAdminSidebarCollapsed" class="admin-sidebar-rail" aria-label="后台管理快捷导航">
      <button class="admin-rail-button" type="button" data-tooltip="展开导航栏" @click="toggleAdminSidebar">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="4" y="5" width="16" height="14" rx="3" />
          <path d="M9 5v14" />
        </svg>
      </button>
      <button class="admin-rail-button" type="button" :class="{ active: activeModule === 'dashboard' }" data-tooltip="DashBoard" @click="navigateTo('/admin')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 13h6V4H4v9Z" />
          <path d="M14 20h6V4h-6v16Z" />
          <path d="M4 20h6v-3H4v3Z" />
        </svg>
      </button>
      <button class="admin-rail-button" type="button" :class="{ active: activeModule === 'knowledge' }" data-tooltip="知识库管理" @click="navigateTo('/admin/knowledge')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 5.5A2.5 2.5 0 0 1 7.5 3H20v16H7.5A2.5 2.5 0 0 0 5 21.5v-16Z" />
          <path d="M5 5.5A2.5 2.5 0 0 0 2.5 3H2v16h.5A2.5 2.5 0 0 1 5 21.5" />
          <path d="M9 8h7" />
          <path d="M9 12h6" />
        </svg>
      </button>
      <button class="admin-rail-button admin-rail-bottom" type="button" data-tooltip="返回会话" @click="emit('back-to-chat')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" />
          <path d="M9 12h12" />
        </svg>
      </button>
    </nav>

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
          <strong>后台管理</strong>
          <small>{{ props.currentUser.displayName || props.currentUser.username }}</small>
        </div>
        <button class="admin-sidebar-toggle" type="button" @click="toggleAdminSidebar">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="4" y="5" width="16" height="14" rx="3" />
            <path d="M9 5v14" />
          </svg>
        </button>
      </div>

      <nav class="admin-nav" aria-label="后台管理导航">
        <button type="button" :class="{ active: activeModule === 'dashboard' }" @click="navigateTo('/admin')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 13h6V4H4v9Z" />
            <path d="M14 20h6V4h-6v16Z" />
            <path d="M4 20h6v-3H4v3Z" />
          </svg>
          <span>DashBoard</span>
        </button>
        <button type="button" :class="{ active: activeModule === 'knowledge' }" @click="navigateTo('/admin/knowledge')">
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
          <span>{{ currentHeader.label }}</span>
          <h1>
            <i class="admin-title-icon" :class="`admin-title-icon-${currentHeader.icon}`" aria-hidden="true">
              <svg v-if="currentHeader.icon === 'dashboard'" viewBox="0 0 24 24">
                <path d="M4 13h6V4H4v9Z" />
                <path d="M14 20h6V4h-6v16Z" />
                <path d="M4 20h6v-3H4v3Z" />
              </svg>
              <svg v-else-if="currentHeader.icon === 'documents'" viewBox="0 0 24 24">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                <path d="M14 2v6h6" />
                <path d="M8 13h8" />
                <path d="M8 17h6" />
              </svg>
              <svg v-else-if="currentHeader.icon === 'chunks'" viewBox="0 0 24 24">
                <path d="M4 7h16" />
                <path d="M4 12h10" />
                <path d="M4 17h16" />
                <path d="M17 10l3 2-3 2" />
              </svg>
              <svg v-else viewBox="0 0 24 24">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15Z" />
                <path d="M8 7h8" />
                <path d="M8 11h6" />
              </svg>
            </i>
            {{ currentHeader.title }}
          </h1>
        </div>
        <button type="button" class="admin-refresh-button" :class="{ refreshing: isRefreshing }" :disabled="isRefreshing" @click="refreshCurrentView">
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

      <section v-if="activeModule === 'dashboard'" class="admin-section kc-content">
        <div class="kc-metric-grid">
          <article class="kc-metric-card">
            <span>活跃用户</span>
            <strong>{{ isLoadingDashboard ? '...' : metricText(dashboard?.activeUserCount) }}</strong>
            <small>最近 24 小时登录</small>
          </article>
          <article class="kc-metric-card">
            <span>消息数</span>
            <strong>{{ isLoadingDashboard ? '...' : metricText(dashboard?.messageCount) }}</strong>
            <small>全量 chat_message</small>
          </article>
          <article class="kc-metric-card">
            <span>会话数</span>
            <strong>{{ isLoadingDashboard ? '...' : metricText(dashboard?.conversationCount) }}</strong>
            <small>全量会话</small>
          </article>
          <article class="kc-metric-card">
            <span>流量数</span>
            <strong>{{ isLoadingDashboard ? '...' : metricText(dashboard?.trafficCharacterCount) }}</strong>
            <small>消息字符量</small>
          </article>
          <article class="kc-metric-card" :class="averageResponseStatus">
            <span>平均响应时间</span>
            <strong>{{ formatDuration(dashboard?.averageResponseTimeMs) }}</strong>
            <small>10 秒内良好，超过 15 秒标红</small>
          </article>
          <article class="kc-metric-card muted">
            <span>知识错误率</span>
            <strong>待接入</strong>
            <small>RAG 评估完善后统计</small>
          </article>
          <article class="kc-metric-card muted">
            <span>无知识率</span>
            <strong>待接入</strong>
            <small>RAG 检索链路完善后统计</small>
          </article>
        </div>
      </section>

      <section v-else class="admin-section kc-content">
        <template v-if="currentView === 'bases'">
          <div class="kc-metric-grid knowledge-metrics">
            <article class="kc-metric-card icon-card">
              <span class="kc-metric-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                  <path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15Z" />
                </svg>
              </span>
              <span>知识库</span>
              <strong>{{ metricText(overview?.knowledgeBaseCount) }}</strong>
            </article>
            <article class="kc-metric-card icon-card">
              <span class="kc-metric-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                  <path d="M14 2v6h6" />
                  <path d="M8 13h8" />
                  <path d="M8 17h6" />
                </svg>
              </span>
              <span>文档数</span>
              <strong>{{ metricText(overview?.totalDocumentCount) }}</strong>
            </article>
            <article class="kc-metric-card icon-card">
              <span class="kc-metric-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M3 7h6l2 2h10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
                  <path d="M7 13h10" />
                  <path d="M7 16h6" />
                </svg>
              </span>
              <span>含文档知识库</span>
              <strong>{{ metricText(overview?.knowledgeBaseWithDocumentsCount) }}</strong>
            </article>
          </div>

          <section class="kc-table-card">
            <div class="kc-card-toolbar">
              <div>
                <strong>知识库列表</strong>
                <small>共 {{ filteredKnowledgeBases.length }} 个</small>
              </div>
              <div class="kc-toolbar-actions">
                <button type="button" class="kc-primary-button" @click="openCreateKnowledgeBaseModal">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 5v14" />
                    <path d="M5 12h14" />
                  </svg>
                  <span>新建知识库</span>
                </button>
                <input v-model="baseKeyword" type="search" placeholder="搜索知识库名称" />
              </div>
            </div>
            <div class="kc-table-head knowledge-grid">
              <span>名称</span>
              <span>Embedding模型</span>
              <span>Collection</span>
              <span>文档数</span>
              <span>操作</span>
            </div>
            <div class="kc-table-body">
              <div v-for="base in filteredKnowledgeBases" :key="base.knowledgeBaseId" class="kc-table-row knowledge-grid">
                <button type="button" class="kc-link-cell" @click="openDocuments(base)">{{ base.name }}</button>
                <span>{{ base.embeddingModel }}</span>
                <span class="kc-tag">{{ base.collectionName }}</span>
                <span>{{ base.documentCount }}</span>
                <span class="kc-row-actions">
                  <button type="button" @click="detailKnowledgeBase = base">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 8h.01" /><path d="M11 12h1v4h1" /></svg>
                    <span>详情</span>
                  </button>
                  <button type="button" @click="openEditKnowledgeBaseModal(base)">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" /></svg>
                    <span>编辑</span>
                  </button>
                  <button type="button" @click="openDocuments(base)">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14" /><path d="M5 12h14" /></svg>
                    <span>录入文档</span>
                  </button>
                  <button type="button" class="danger" @click="removeKnowledgeBase(base)">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M19 6l-1 14H6L5 6" /></svg>
                    <span>删除</span>
                  </button>
                </span>
              </div>
            </div>
            <p v-if="!isLoadingKnowledge && filteredKnowledgeBases.length === 0" class="kc-empty">还没有知识库。</p>
          </section>
        </template>

        <template v-else-if="currentView === 'documents'">
          <div class="kc-breadcrumb">首页 / 知识库管理 / 文档管理</div>
          <div class="kc-title-row">
            <div>
              <p>{{ selectedKnowledgeBase?.name || '知识库' }}（{{ selectedKnowledgeBase?.collectionName || selectedKnowledgeBaseId }}）</p>
            </div>
            <div class="kc-title-actions">
              <button type="button" class="kc-ghost-button" @click="navigateTo('/admin/knowledge')">返回知识库</button>
              <button type="button" class="kc-primary-button" @click="openIngestionModal">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                  <path d="M14 2v6h6" />
                  <path d="M12 18v-6" />
                  <path d="M9 15l3-3 3 3" />
                </svg>
                <span>上传文档</span>
              </button>
            </div>
          </div>

          <section class="kc-table-card">
            <div class="kc-card-toolbar">
              <div>
                <strong>文档列表</strong>
                <small>共 {{ filteredDocuments.length }} 条</small>
              </div>
              <div class="kc-toolbar-actions">
                <input v-model="documentKeyword" type="search" placeholder="搜索文档名称" />
                <select v-model="documentStatusFilter">
                  <option value="ALL">全部状态</option>
                  <option value="COMPLETED">success</option>
                  <option value="PROCESSING">processing</option>
                  <option value="FAILED">failed</option>
                </select>
              </div>
            </div>
            <div class="kc-table-head document-grid">
              <span>文档</span>
              <span>来源</span>
              <span>处理模式</span>
              <span>状态</span>
              <span>分块数</span>
              <span>类型</span>
              <span>操作</span>
            </div>
            <div class="kc-table-body">
              <div v-for="doc in filteredDocuments" :key="doc.documentId" class="kc-table-row document-grid">
                <button
                  type="button"
                  class="kc-link-cell kc-cell-tooltip"
                  @mouseenter="showOverflowTooltip($event, doc.fileName)"
                  @mouseleave="clearOverflowTooltip"
                  @click="openDocumentChunks(doc)"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7h6l2 2h10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" /></svg>
                  <span class="kc-tooltip-content">{{ doc.fileName }}</span>
                </button>
                <span class="kc-cell-tooltip" @mouseenter="showOverflowTooltip($event, sourceText(doc))" @mouseleave="clearOverflowTooltip">
                  <span class="kc-tooltip-content">{{ sourceText(doc) }}</span>
                </span>
                <span class="kc-cell-tooltip" @mouseenter="showOverflowTooltip($event, doc.chunkStrategy?.toLowerCase() || 'chunk')" @mouseleave="clearOverflowTooltip">
                  <span class="kc-tooltip-content">{{ doc.chunkStrategy?.toLowerCase() || 'chunk' }}</span>
                </span>
                <span class="kc-status kc-cell-tooltip" :class="statusClass(doc.status)" @mouseenter="showOverflowTooltip($event, statusText(doc.status))" @mouseleave="clearOverflowTooltip">
                  <span class="kc-tooltip-content">{{ statusText(doc.status) }}</span>
                </span>
                <span class="kc-cell-tooltip" @mouseenter="showOverflowTooltip($event, String(doc.chunkCount))" @mouseleave="clearOverflowTooltip">
                  <span class="kc-tooltip-content">{{ doc.chunkCount }}</span>
                </span>
                <span class="kc-cell-tooltip" @mouseenter="showOverflowTooltip($event, typeText(doc))" @mouseleave="clearOverflowTooltip">
                  <span class="kc-tooltip-content">{{ typeText(doc) }}</span>
                </span>
                <span class="kc-row-actions document-actions">
                  <button type="button" aria-label="重新分块" @click.stop="openRechunkModal(doc)">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12a9 9 0 0 1-9 9" /><path d="M3 12a9 9 0 0 1 9-9" /><path d="M21 3v6h-6" /><path d="M3 21v-6h6" /></svg>
                    <span>重新分块</span>
                  </button>
                  <button type="button" aria-label="分块详情" @click="detailDocument = doc">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 8h.01" /><path d="M11 12h1v4h1" /></svg>
                    <span>详情</span>
                  </button>
                  <button type="button" aria-label="查看分块" @click="openDocumentChunks(doc)">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h16v16H4z" /><path d="M8 8h8" /><path d="M8 12h8" /><path d="M8 16h5" /></svg>
                    <span>查看分块</span>
                  </button>
                  <button type="button" class="danger" aria-label="删除文档" @click="removeDocument(doc)">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M19 6l-1 14H6L5 6" /></svg>
                    <span>删除</span>
                  </button>
                </span>
              </div>
            </div>
            <p v-if="filteredDocuments.length === 0" class="kc-empty">这个知识库还没有文档。</p>
          </section>
        </template>

        <template v-else>
          <div class="kc-breadcrumb">首页 / 知识库管理 / 文档管理 / 切片管理</div>
          <div class="kc-title-row">
            <div>
              <p>{{ selectedDocument?.fileName || '文档' }}（知识库: {{ selectedKnowledgeBaseId }}）</p>
            </div>
            <div class="kc-title-actions">
              <button type="button" class="kc-ghost-button" @click="navigateTo(`/admin/knowledge/${selectedKnowledgeBaseId}`)">返回文档</button>
              <button type="button" class="kc-ghost-button" :disabled="isRebuildingVectors" @click="rebuildVectors">
                {{ isRebuildingVectors ? '重建中...' : '重建向量' }}
              </button>
            </div>
          </div>

          <section class="kc-table-card">
            <div class="kc-card-toolbar">
              <div>
                <strong>Chunk 列表</strong>
                <small>共 {{ filteredChunks.length }} 条</small>
              </div>
              <div class="kc-toolbar-actions">
                <div class="kc-select-menu" :class="{ open: isChunkStatusMenuOpen }">
                  <button type="button" class="kc-select-trigger" @click="isChunkStatusMenuOpen = !isChunkStatusMenuOpen">
                    <span>{{ statusFilterText(chunkStatusFilter) }}</span>
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
                  </button>
                  <div v-if="isChunkStatusMenuOpen" class="kc-select-options">
                    <button type="button" :class="{ active: chunkStatusFilter === 'ALL' }" @click="setChunkStatus('ALL')">全部状态</button>
                    <button type="button" :class="{ active: chunkStatusFilter === 'ENABLED' }" @click="setChunkStatus('ENABLED')">启用</button>
                    <button type="button" :class="{ active: chunkStatusFilter === 'DISABLED' }" @click="setChunkStatus('DISABLED')">禁用</button>
                  </div>
                </div>
                <button type="button" class="kc-ghost-button" :disabled="selectedChunkIds.size === 0" @click="setSelectedChunksEnabled(true)">批量启用</button>
                <button type="button" class="kc-ghost-button" :disabled="selectedChunkIds.size === 0" @click="setSelectedChunksEnabled(false)">批量禁用</button>
                <button type="button" class="kc-ghost-button" @click="setAllChunksEnabled(true)">全量启用</button>
                <button type="button" class="kc-ghost-button" @click="setAllChunksEnabled(false)">全量禁用</button>
              </div>
            </div>
            <div class="kc-table-head chunk-grid">
              <span><input type="checkbox" :checked="filteredChunks.length > 0 && filteredChunks.every((chunk) => selectedChunkIds.has(chunk.chunkId))" @change="toggleAllVisibleChunks" /></span>
              <span>序号</span>
              <span>内容</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div class="kc-table-body">
              <div v-for="chunk in filteredChunks" :key="chunk.chunkId" class="kc-table-row chunk-grid">
                <span><input type="checkbox" :checked="selectedChunkIds.has(chunk.chunkId)" @change="toggleChunkSelected(chunk.chunkId)" /></span>
                <span>{{ chunk.chunkIndex }}</span>
                <p>{{ chunk.content }}</p>
                <span class="kc-status" :class="chunk.enabled ? 'success' : 'muted'">{{ chunk.enabled ? '启用' : '禁用' }}</span>
                <span class="kc-row-actions compact">
                  <button type="button" @click="detailChunk = chunk">详情</button>
                  <button type="button" @click="viewingChunk = chunk">查看</button>
                  <button type="button" @click="openEditChunkModal(chunk)">修改</button>
                  <button type="button" @click="toggleChunk(chunk)">{{ chunk.enabled ? '禁用' : '启用' }}</button>
                  <button type="button" class="danger" @click="removeChunk(chunk)">删除</button>
                </span>
              </div>
            </div>
            <p v-if="filteredChunks.length === 0" class="kc-empty">这个文档还没有分块。</p>
          </section>
        </template>
      </section>
    </section>

    <div v-if="isCreateModalOpen" class="kc-modal-backdrop" @click.self="closeCreateKnowledgeBaseModal">
      <section class="kc-modal">
        <header>
          <div>
            <h2>新建知识库</h2>
            <p>创建一个新的 collection</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="closeCreateKnowledgeBaseModal">×</button>
        </header>
        <form class="kc-form" @submit.prevent="handleCreateKnowledgeBase">
          <p v-if="createFormError" class="kc-form-error">{{ createFormError }}</p>
          <label>
            <span>知识库名称</span>
            <input v-model="createForm.name" type="text" placeholder="您的知识库名称" />
          </label>
          <label>
            <span>Embedding 模型</span>
            <input v-model="createForm.embeddingModel" type="text" readonly />
          </label>
          <label>
            <span>Collection 名称</span>
            <input v-model="createForm.collectionName" type="text" placeholder="您的知识库唯一标识" />
          </label>
          <footer>
            <button type="button" class="kc-ghost-button" @click="closeCreateKnowledgeBaseModal">取消</button>
            <button type="submit" class="kc-primary-button" :disabled="!canCreateKnowledgeBase || isCreatingKnowledgeBase">
              {{ isCreatingKnowledgeBase ? '创建中...' : '创建' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <Teleport to="body">
      <Transition name="delete-dialog">
        <div
          v-if="deleteKnowledgeBaseDialog.open"
          class="delete-dialog-backdrop"
          @click.self="closeDeleteKnowledgeBaseDialog"
        >
          <section
            class="delete-dialog-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="knowledge-delete-dialog-title"
            aria-describedby="knowledge-delete-dialog-description"
          >
            <button
              type="button"
              class="delete-dialog-close"
              aria-label="关闭删除确认"
              :disabled="isDeletingKnowledgeBase"
              @click="closeDeleteKnowledgeBaseDialog"
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
              <h2 id="knowledge-delete-dialog-title">永久删除这个知识库？</h2>
              <p id="knowledge-delete-dialog-description">
                知识库、文档、分块和向量数据会一起删除，删除后无法恢复。
              </p>
            </div>

            <div class="delete-dialog-target">
              <span>将被删除</span>
              <strong>{{ deleteKnowledgeBaseDialog.knowledgeBase?.name || '这个知识库' }}</strong>
            </div>

            <p class="delete-dialog-warning">
              如果只是暂时不用，可以先取消，后续再做禁用或归档会更稳。
            </p>

            <p v-if="deleteKnowledgeBaseError" class="delete-dialog-error">
              {{ deleteKnowledgeBaseError }}
            </p>

            <div class="delete-dialog-actions">
              <button
                type="button"
                class="delete-dialog-secondary"
                :disabled="isDeletingKnowledgeBase"
                autofocus
                @click="closeDeleteKnowledgeBaseDialog"
              >
                先留着
              </button>
              <button
                type="button"
                class="delete-dialog-danger"
                :disabled="isDeletingKnowledgeBase"
                @click="confirmDeleteKnowledgeBase"
              >
                {{ isDeletingKnowledgeBase ? '删除中...' : '永久删除' }}
              </button>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="delete-dialog">
        <div
          v-if="deleteDocumentDialog.open"
          class="delete-dialog-backdrop"
          @click.self="closeDeleteDocumentDialog"
        >
          <section
            class="delete-dialog-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="document-delete-dialog-title"
            aria-describedby="document-delete-dialog-description"
          >
            <button
              type="button"
              class="delete-dialog-close"
              aria-label="关闭删除确认"
              :disabled="isDeletingDocument"
              @click="closeDeleteDocumentDialog"
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
              <h2 id="document-delete-dialog-title">永久删除这个文档？</h2>
              <p id="document-delete-dialog-description">
                文档、分块和向量数据会一起删除，删除后无法恢复。
              </p>
            </div>

            <div class="delete-dialog-target">
              <span>将被删除</span>
              <strong>{{ deleteDocumentDialog.document?.fileName || '这个文档' }}</strong>
            </div>

            <p class="delete-dialog-warning">
              删除后对应的检索内容会立即不可用，请确认这个文档已经不再需要。
            </p>

            <p v-if="deleteDocumentError" class="delete-dialog-error">
              {{ deleteDocumentError }}
            </p>

            <div class="delete-dialog-actions">
              <button
                type="button"
                class="delete-dialog-secondary"
                :disabled="isDeletingDocument"
                autofocus
                @click="closeDeleteDocumentDialog"
              >
                先留着
              </button>
              <button
                type="button"
                class="delete-dialog-danger"
                :disabled="isDeletingDocument"
                @click="confirmDeleteDocument"
              >
                {{ isDeletingDocument ? '删除中...' : '永久删除' }}
              </button>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="delete-dialog">
        <div
          v-if="deleteChunkDialog.open"
          class="delete-dialog-backdrop"
          @click.self="closeDeleteChunkDialog"
        >
          <section
            class="delete-dialog-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="chunk-delete-dialog-title"
            aria-describedby="chunk-delete-dialog-description"
          >
            <button
              type="button"
              class="delete-dialog-close"
              aria-label="关闭删除确认"
              :disabled="isDeletingChunk"
              @click="closeDeleteChunkDialog"
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
              <h2 id="chunk-delete-dialog-title">永久删除这个分块？</h2>
              <p id="chunk-delete-dialog-description">
                该分块和对应向量数据会一起删除，删除后不会再参与检索。
              </p>
            </div>

            <div class="delete-dialog-target">
              <span>将被删除</span>
              <strong>分块 #{{ (deleteChunkDialog.chunk?.chunkIndex ?? 0) + 1 }}</strong>
            </div>

            <p class="delete-dialog-warning">
              如果只是暂时不想检索到它，可以先取消，然后使用禁用操作。
            </p>

            <p v-if="deleteChunkError" class="delete-dialog-error">
              {{ deleteChunkError }}
            </p>

            <div class="delete-dialog-actions">
              <button
                type="button"
                class="delete-dialog-secondary"
                :disabled="isDeletingChunk"
                autofocus
                @click="closeDeleteChunkDialog"
              >
                先留着
              </button>
              <button
                type="button"
                class="delete-dialog-danger"
                :disabled="isDeletingChunk"
                @click="confirmDeleteChunk"
              >
                {{ isDeletingChunk ? '删除中...' : '永久删除' }}
              </button>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>

    <div v-if="isEditModalOpen" class="kc-modal-backdrop" @click.self="isEditModalOpen = false">
      <section class="kc-modal">
        <header>
          <div>
            <h2>编辑知识库</h2>
            <p>{{ editKnowledgeBase?.collectionName }}</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="isEditModalOpen = false">×</button>
        </header>
        <form class="kc-form" @submit.prevent="submitEditKnowledgeBase">
          <label>
            <span>知识库名称</span>
            <input v-model="editForm.name" type="text" placeholder="知识库名称" />
          </label>
          <footer>
            <button type="button" class="kc-ghost-button" @click="isEditModalOpen = false">取消</button>
            <button type="submit" class="kc-primary-button" :disabled="!editForm.name.trim()">保存</button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="detailKnowledgeBase" class="kc-modal-backdrop" @click.self="detailKnowledgeBase = null">
      <section class="kc-modal">
        <header>
          <div>
            <h2>知识库详情</h2>
            <p>{{ detailKnowledgeBase.name }}（{{ detailKnowledgeBase.collectionName }}）</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="detailKnowledgeBase = null">×</button>
        </header>
        <div class="kc-detail-grid">
          <span>Embedding 模型</span>
          <strong>{{ detailKnowledgeBase.embeddingModel }}</strong>
          <span>文档数</span>
          <strong>{{ detailKnowledgeBase.documentCount }}</strong>
          <span>创建时间</span>
          <strong>{{ formatDate(detailKnowledgeBase.createdAt) }}</strong>
          <span>更新时间</span>
          <strong>{{ formatDate(detailKnowledgeBase.updatedAt) }}</strong>
        </div>
        <footer>
          <button type="button" class="kc-ghost-button" @click="detailKnowledgeBase = null">关闭</button>
        </footer>
      </section>
    </div>

    <div v-if="isIngestionModalOpen" class="kc-modal-backdrop" @click.self="isIngestionModalOpen = false">
      <section class="kc-modal wide">
        <header>
          <div>
            <h2>上传文档</h2>
            <p>{{ selectedKnowledgeBase?.name }}</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="isIngestionModalOpen = false">×</button>
        </header>
        <form class="kc-form grid" @submit.prevent="submitIngestion">
          <p v-if="ingestionFormError" class="kc-form-error full">{{ ingestionFormError }}</p>
          <label>
            <span>录入方式</span>
            <select v-model="ingestionMode">
              <option value="upload">本地上传</option>
              <option value="url">URL 解析</option>
            </select>
          </label>
          <label v-if="ingestionMode === 'upload'" class="kc-upload-field">
            <span>文件</span>
            <input class="kc-file-input" type="file" accept=".pdf,.md,.txt,.doc,.docx" @change="handleFileChange" />
            <span class="kc-upload-box">
              <span class="kc-upload-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                  <path d="M14 2v6h6" />
                  <path d="M12 18v-6" />
                  <path d="M9 15l3-3 3 3" />
                </svg>
              </span>
              <span class="kc-upload-copy">
                <strong>{{ selectedFile?.name || '选择文档文件' }}</strong>
                <small>{{ selectedFile ? formatBytes(selectedFile.size) : '支持 PDF、Markdown、TXT、Word' }}</small>
              </span>
              <span class="kc-upload-action">{{ selectedFile ? '重新选择' : '浏览文件' }}</span>
            </span>
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
          <template v-if="ingestionOptions.strategy === 'RECURSIVE'">
          <label>
            <span>分块大小</span>
            <input v-model.number="ingestionOptions.chunkSize" type="number" min="100" />
          </label>
          <label>
            <span>重叠大小</span>
            <input v-model.number="ingestionOptions.chunkOverlap" type="number" min="0" />
          </label>
          <label>
            <span>最大块数</span>
            <input v-model.number="ingestionOptions.maxChunks" type="number" min="1" />
          </label>
          </template>
          <footer>
            <button type="button" class="kc-ghost-button" @click="isIngestionModalOpen = false">取消</button>
            <button type="submit" class="kc-primary-button" :disabled="!canIngest">
              {{ isIngesting ? '加工中...' : '开始入库' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="isRechunkModalOpen" class="kc-modal-backdrop" @click.self="closeRechunkModal">
      <section class="kc-modal">
        <header>
          <div>
            <h2>重新分块</h2>
            <p>{{ rechunkDocument?.fileName }}</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" :disabled="isRechunking" @click="closeRechunkModal">×</button>
        </header>
        <form class="kc-form" @submit.prevent="submitRechunk">
          <p v-if="rechunkFormError" class="kc-form-error">{{ rechunkFormError }}</p>
          <label>
            <span>分块策略</span>
            <select v-model="rechunkForm.strategy">
              <option value="RECURSIVE">递归切块</option>
              <option value="AUTO">自动策略</option>
              <option value="NONE">不分块</option>
            </select>
          </label>
          <template v-if="rechunkForm.strategy === 'RECURSIVE'">
          <label>
            <span>分块大小</span>
            <input v-model.number="rechunkForm.chunkSize" type="number" min="100" />
          </label>
          <label>
            <span>重叠大小</span>
            <input v-model.number="rechunkForm.chunkOverlap" type="number" min="0" />
          </label>
          <label>
            <span>最大块数</span>
            <input v-model.number="rechunkForm.maxChunks" type="number" min="1" />
          </label>
          </template>
          <footer>
            <button type="button" class="kc-ghost-button" :disabled="isRechunking" @click="closeRechunkModal">取消</button>
            <button type="submit" class="kc-primary-button" :disabled="isRechunking">
              {{ isRechunking ? '处理中...' : '执行分块' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="detailDocument" class="kc-modal-backdrop" @click.self="detailDocument = null">
      <section class="kc-modal wide">
        <header>
          <div>
            <h2>分块详情</h2>
            <p>文档 [{{ detailDocument.fileName }}] 的分块执行日志</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="detailDocument = null">×</button>
        </header>
        <div class="kc-detail-panel grouped">
          <section class="kc-detail-section">
            <h3>基础信息</h3>
            <div>
              <span>执行状态: <strong :class="statusClass(detailDocument.status)">{{ statusText(detailDocument.status) }}</strong></span>
              <span>执行时间: {{ formatDate(detailDocument.updatedAt) }}</span>
              <span>文件大小: {{ formatBytes(detailDocument.originalSizeBytes) }}</span>
              <span>更新时间: {{ formatDate(detailDocument.updatedAt) }}</span>
              <span>处理模式: 分块策略</span>
              <span>分块策略: {{ detailDocument.chunkStrategy?.toLowerCase() }}</span>
              <span>分块数量: {{ detailDocument.chunkCount }}</span>
            </div>
          </section>
          <section class="kc-detail-section">
            <h3>耗时信息</h3>
            <div>
              <span>文本提取: {{ formatDuration(detailDocument.parseDurationMs) }}</span>
              <span>分块耗时: {{ formatDuration(detailDocument.chunkDurationMs) }}</span>
              <span>向量化: {{ formatDuration(detailDocument.embeddingDurationMs) }}</span>
              <span>其他耗时: {{ formatDuration(detailDocument.otherDurationMs) }}</span>
              <span>总耗时: {{ formatDuration(detailDocument.totalDurationMs) }}</span>
            </div>
          </section>
        </div>
        <footer>
          <button type="button" class="kc-ghost-button" @click="detailDocument = null">关闭</button>
        </footer>
      </section>
    </div>

    <div v-if="detailChunk" class="kc-modal-backdrop" @click.self="detailChunk = null">
      <section class="kc-modal wide">
        <header>
          <div>
            <h2>分块详情</h2>
            <p>分块 #{{ detailChunk.chunkIndex }} 的元数据</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="detailChunk = null">×</button>
        </header>
        <div class="kc-detail-panel grouped">
          <section class="kc-detail-section">
            <h3>基础信息</h3>
            <div>
              <span>分块序号: {{ detailChunk.chunkIndex }}</span>
              <span>状态: <strong :class="detailChunk.enabled ? 'success' : 'pending'">{{ detailChunk.enabled ? '启用' : '禁用' }}</strong></span>
              <span>字符数: {{ detailChunk.charCount }}</span>
              <span>Token数: {{ detailChunk.tokenCount }}</span>
              <span>更新时间: {{ formatDate(detailChunk.updatedAt) }}</span>
            </div>
          </section>
        </div>
        <footer>
          <button type="button" class="kc-ghost-button" @click="detailChunk = null">关闭</button>
        </footer>
      </section>
    </div>

    <div v-if="viewingChunk" class="kc-modal-backdrop" @click.self="viewingChunk = null">
      <section class="kc-modal wide">
        <header>
          <div>
            <h2>查看分块</h2>
            <p>分块 #{{ viewingChunk.chunkIndex }} 的完整内容</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="viewingChunk = null">×</button>
        </header>
        <pre class="kc-chunk-content-panel">{{ viewingChunk.content }}</pre>
        <footer>
          <button type="button" class="kc-ghost-button" @click="viewingChunk = null">关闭</button>
        </footer>
      </section>
    </div>

    <div v-if="editingChunk" class="kc-modal-backdrop" @click.self="closeEditChunkModal">
      <section class="kc-modal wide">
        <header>
          <div>
            <h2>修改分块</h2>
            <p>分块 #{{ editingChunk.chunkIndex }} 的内容</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="closeEditChunkModal">×</button>
        </header>
        <form class="kc-form" @submit.prevent="submitEditChunk">
          <label>
            分块内容
            <textarea v-model="editChunkForm.content" class="kc-chunk-editor" rows="14"></textarea>
          </label>
          <p v-if="editChunkError" class="kc-modal-error">{{ editChunkError }}</p>
          <footer>
            <button type="button" class="kc-ghost-button" :disabled="isUpdatingChunk" @click="closeEditChunkModal">取消</button>
            <button type="submit" class="kc-primary-button" :disabled="isUpdatingChunk">
              {{ isUpdatingChunk ? '保存中...' : '确定修改' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="isChunkUpdateNoticeOpen" class="kc-modal-backdrop" @click.self="isChunkUpdateNoticeOpen = false">
      <section class="kc-modal">
        <header>
          <div>
            <h2>修改成功</h2>
            <p>修改分块后请重建向量</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="isChunkUpdateNoticeOpen = false">×</button>
        </header>
        <div class="kc-notice-panel">
          <strong>当前只更新了分块文本和统计信息。</strong>
          <span>为了让检索结果使用新内容，请回到分块管理顶部点击“重建向量”。</span>
        </div>
        <footer>
          <button type="button" class="kc-ghost-button" @click="isChunkUpdateNoticeOpen = false">我知道了</button>
        </footer>
      </section>
    </div>
  </main>
</template>
