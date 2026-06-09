<script setup>
import { LineChart } from 'echarts/charts';
import { GridComponent, MarkLineComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  createKnowledgeBase,
  createTerminologyMapping,
  deleteChunk,
  deleteIngestionTask,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  deleteTerminologyMapping,
  fetchAdminDashboard,
  fetchDocumentChunks,
  fetchFailedIngestionTasks,
  fetchKnowledgeBase,
  fetchKnowledgeBases,
  fetchKnowledgeDocument,
  fetchKnowledgeDocuments,
  fetchKnowledgeOverview,
  fetchQueryPipelineConfig,
  fetchTerminologyMappings,
  ingestKnowledgeUrl,
  rebuildDocumentVectors,
  rechunkKnowledgeDocument,
  retryIngestionTask,
  updateChunk,
  updateChunkEnabled,
  updateDocumentChunksEnabled,
  updateKnowledgeBase,
  updateQueryPipelineConfig,
  updateTerminologyMapping,
  updateTerminologyMappingEnabled,
  uploadKnowledgeDocument
} from '../api/adminApi';
import { createQuietReveal } from '../utils/quietMotion';

echarts.use([LineChart, GridComponent, TooltipComponent, MarkLineComponent, CanvasRenderer]);

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
const uploadingDocuments = ref([]);
const chunks = ref([]);
const failedTasks = ref([]);
const terminologyMappings = ref([]);
const queryPipelineConfig = ref(null);
const selectedKnowledgeBase = ref(null);
const selectedDocument = ref(null);
const selectedChunkIds = ref(new Set());
const isLoadingDashboard = ref(false);
const isLoadingKnowledge = ref(false);
const isLoadingTasks = ref(false);
const isLoadingMappings = ref(false);
const isLoadingPipeline = ref(false);
const isCreatingKnowledgeBase = ref(false);
const isSavingMapping = ref(false);
const isSavingPipeline = ref(false);
const isIngesting = ref(false);
const isRechunking = ref(false);
const isRebuildingVectors = ref(false);
const isDeletingKnowledgeBase = ref(false);
const isDeletingDocument = ref(false);
const isDeletingChunk = ref(false);
const isDeletingMapping = ref(false);
const isUpdatingChunk = ref(false);
const retryingTaskId = ref('');
const deletingTaskId = ref('');
const isRefreshing = ref(false);
const isAdminSidebarCollapsed = ref(false);
const adminError = ref('');
const createFormError = ref('');
const ingestionFormError = ref('');
const rechunkFormError = ref('');
const baseKeyword = ref('');
const documentKeyword = ref('');
const taskKeyword = ref('');
const mappingKeyword = ref('');
const documentStatusFilter = ref('ALL');
const taskStatusFilter = ref('ALL');
const chunkStatusFilter = ref('ALL');
const messageTrendRange = ref('day');
const activeTrendType = ref('message');
const isCreateModalOpen = ref(false);
const isMappingModalOpen = ref(false);
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
const deleteTaskDialog = ref({
  open: false,
  task: null
});
const deleteTaskError = ref('');
const deleteMappingDialog = ref({
  open: false,
  mapping: null
});
const deleteMappingError = ref('');
const isIngestionModalOpen = ref(false);
const isRechunkModalOpen = ref(false);
const detailDocument = ref(null);
const detailChunk = ref(null);
const detailTask = ref(null);
const viewingChunk = ref(null);
const editingChunk = ref(null);
const editChunkForm = ref({
  content: ''
});
const editChunkError = ref('');
const isChunkUpdateNoticeOpen = ref(false);
const isTaskStatusMenuOpen = ref(false);
const isDocumentStatusMenuOpen = ref(false);
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
const rechunkForm = ref(defaultChunkOptions());
const rechunkDocument = ref(null);
const editKnowledgeBase = ref(null);
const editForm = ref({
  name: ''
});
const mappingForm = ref(defaultMappingForm());
const editingMapping = ref(null);
const mappingFormError = ref('');
const pipelineForm = ref(defaultPipelineForm());
const pipelineFormError = ref('');
const floatingTooltip = ref({
  visible: false,
  text: '',
  left: 0,
  top: 0,
  placement: 'top'
});
const adminMain = ref(null);
const dashboardTrendShell = ref(null);
const dashboardTrendChart = ref(null);
const dashboardTrendOverlay = ref(null);
let knowledgePollTimer = null;
let stopAdminMotion = null;
let dashboardChart = null;
let dashboardTrendAnimationFrame = 0;

const trendTypeOptions = [
  { type: 'message', label: '消息' },
  { type: 'conversation', label: '会话' },
  { type: 'responseTime', label: '响应时间' },
  { type: 'activeUser', label: '活跃用户' }
];

const activeModule = computed(() => route.value.module);
const currentView = computed(() => route.value.view);
const currentHeader = computed(() => {
  if (activeModule.value === 'dashboard') {
    return { label: '系统指标', title: 'DashBoard', icon: 'dashboard' };
  }
  if (activeModule.value === 'tasks') {
    return { label: '入库任务', title: '失败任务', icon: 'tasks' };
  }
  if (activeModule.value === 'mappings') {
    return { label: '查询预处理', title: '关键词映射', icon: 'mappings' };
  }
  if (activeModule.value === 'pipeline') {
    return { label: '会话流水线', title: 'Pipeline 配置', icon: 'pipeline' };
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
const isSelectedDocumentProcessing = computed(() => selectedDocument.value?.status === 'PROCESSING');
const canMutateSelectedChunks = computed(() => selectedDocument.value && !isBusyDocumentStatus(selectedDocument.value.status));
const canCreateKnowledgeBase = computed(() => {
  return createForm.value.name.trim() && createForm.value.embeddingModel.trim() && createForm.value.collectionName.trim();
});
const canIngest = computed(() => {
  if (!selectedKnowledgeBaseId.value || isIngesting.value) {
    return false;
  }
  return ingestionMode.value === 'upload' ? Boolean(selectedFile.value) : Boolean(urlForm.value.url.trim());
});
const filteredTerminologyMappings = computed(() => {
  const keyword = mappingKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return terminologyMappings.value;
  }
  return terminologyMappings.value.filter((mapping) => {
    return [
      mapping.aliasName,
      mapping.canonicalName,
      mapping.termType,
      mapping.description
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
});
const enabledMappingCount = computed(() => terminologyMappings.value.filter((mapping) => mapping.enabled).length);
const terminologyTermCount = computed(() => new Set(
  terminologyMappings.value
    .map((mapping) => mapping.canonicalName)
    .filter(Boolean)
).size);
const canSubmitMapping = computed(() => {
  return mappingForm.value.aliasName.trim() && mappingForm.value.canonicalName.trim() && !isSavingMapping.value;
});
const isPipelineDirty = computed(() => {
  if (!queryPipelineConfig.value) {
    return false;
  }
  return JSON.stringify(normalizePipelinePayload(pipelineForm.value)) !== JSON.stringify(normalizePipelinePayload(queryPipelineConfig.value));
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
    const rect = target.getBoundingClientRect();
    const maxWidth = Math.min(420, window.innerWidth * 0.6);
    const left = Math.min(Math.max(12, rect.left), Math.max(12, window.innerWidth - maxWidth - 12));
    const hasTopSpace = rect.top > 96;
    floatingTooltip.value = {
      visible: true,
      text,
      left,
      top: hasTopSpace ? rect.top - 10 : rect.bottom + 10,
      placement: hasTopSpace ? 'top' : 'bottom'
    };
  } else {
    clearOverflowTooltip(event);
  }
}

function clearOverflowTooltip(event) {
  if (event?.currentTarget) {
    delete event.currentTarget.dataset.tooltip;
  }
  floatingTooltip.value.visible = false;
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

function documentStatusFilterText(value) {
  if (value === 'UPLOADING') {
    return 'uploading';
  }
  if (value === 'UPLOADED') {
    return 'uploaded';
  }
  if (value === 'COMPLETED') {
    return 'success';
  }
  if (value === 'PROCESSING') {
    return 'processing';
  }
  if (value === 'FAILED') {
    return 'failed';
  }
  return '全部状态';
}

function taskStatusFilterText(value) {
  if (value === 'FAILED') {
    return 'failed';
  }
  if (value === 'DEAD') {
    return 'dead';
  }
  return '全部状态';
}

function toggleTaskStatusMenu() {
  isTaskStatusMenuOpen.value = !isTaskStatusMenuOpen.value;
  isDocumentStatusMenuOpen.value = false;
  isChunkStatusMenuOpen.value = false;
}

function toggleDocumentStatusMenu() {
  isDocumentStatusMenuOpen.value = !isDocumentStatusMenuOpen.value;
  isTaskStatusMenuOpen.value = false;
  isChunkStatusMenuOpen.value = false;
}

function toggleChunkStatusMenu() {
  isChunkStatusMenuOpen.value = !isChunkStatusMenuOpen.value;
  isTaskStatusMenuOpen.value = false;
  isDocumentStatusMenuOpen.value = false;
}

function setTaskStatus(value) {
  taskStatusFilter.value = value;
  isTaskStatusMenuOpen.value = false;
}

function setDocumentStatus(value) {
  documentStatusFilter.value = value;
  isDocumentStatusMenuOpen.value = false;
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

function defaultMappingForm() {
  return {
    aliasName: '',
    canonicalName: '',
    termType: 'TECH',
    description: '',
    priority: 100,
    enabled: true
  };
}

function defaultPipelineForm() {
  return {
    terminologyEnabled: true,
    llmRewriteEnabled: true,
    ruleSplitEnabled: true,
    fallbackPolicy: 'TERM_ONLY',
    rewriteTimeoutMs: 3000,
    rewriteContextTurns: 3
  };
}

function normalizePipelinePayload(value) {
  return {
    terminologyEnabled: value?.terminologyEnabled !== false,
    llmRewriteEnabled: value?.llmRewriteEnabled !== false,
    ruleSplitEnabled: value?.ruleSplitEnabled !== false,
    fallbackPolicy: value?.fallbackPolicy || 'TERM_ONLY',
    rewriteTimeoutMs: Number(value?.rewriteTimeoutMs || 3000),
    rewriteContextTurns: Number(value?.rewriteContextTurns || 3)
  };
}

function openCreateMappingModal() {
  editingMapping.value = null;
  mappingFormError.value = '';
  mappingForm.value = defaultMappingForm();
  isMappingModalOpen.value = true;
}

function openEditMappingModal(mapping) {
  editingMapping.value = mapping;
  mappingFormError.value = '';
  mappingForm.value = {
    aliasName: mapping.aliasName || '',
    canonicalName: mapping.canonicalName || '',
    termType: mapping.termType || 'TECH',
    description: mapping.description || '',
    priority: mapping.priority ?? 0,
    enabled: mapping.enabled !== false
  };
  isMappingModalOpen.value = true;
}

function closeMappingModal() {
  if (isSavingMapping.value) {
    return;
  }
  isMappingModalOpen.value = false;
  mappingFormError.value = '';
  editingMapping.value = null;
}

async function submitMappingForm() {
  if (!canSubmitMapping.value) {
    return;
  }
  isSavingMapping.value = true;
  mappingFormError.value = '';
  try {
    const payload = {
      aliasName: mappingForm.value.aliasName.trim(),
      canonicalName: mappingForm.value.canonicalName.trim(),
      termType: mappingForm.value.termType || 'TECH',
      description: mappingForm.value.description.trim(),
      priority: Number(mappingForm.value.priority || 0),
      enabled: mappingForm.value.enabled
    };
    if (editingMapping.value?.aliasId) {
      await updateTerminologyMapping(editingMapping.value.aliasId, payload);
    } else {
      await createTerminologyMapping(payload);
    }
    await loadTerminologyMappings();
    isMappingModalOpen.value = false;
    editingMapping.value = null;
  } catch (error) {
    mappingFormError.value = error.message || '关键词映射保存失败';
  } finally {
    isSavingMapping.value = false;
  }
}

async function toggleMappingEnabled(mapping) {
  try {
    await updateTerminologyMappingEnabled(mapping.aliasId, !mapping.enabled);
    await loadTerminologyMappings();
  } catch (error) {
    handleAdminError(error);
  }
}

function openDeleteMappingDialog(mapping) {
  deleteMappingError.value = '';
  deleteMappingDialog.value = {
    open: true,
    mapping
  };
}

function closeDeleteMappingDialog() {
  if (isDeletingMapping.value) {
    return;
  }
  deleteMappingDialog.value = {
    open: false,
    mapping: null
  };
  deleteMappingError.value = '';
}

async function confirmDeleteMapping() {
  const mapping = deleteMappingDialog.value.mapping;
  if (!mapping?.aliasId) {
    return;
  }
  isDeletingMapping.value = true;
  deleteMappingError.value = '';
  try {
    await deleteTerminologyMapping(mapping.aliasId);
    await loadTerminologyMappings();
    deleteMappingDialog.value = {
      open: false,
      mapping: null
    };
  } catch (error) {
    deleteMappingError.value = error.message || '关键词映射删除失败';
  } finally {
    isDeletingMapping.value = false;
  }
}

async function submitPipelineConfig() {
  isSavingPipeline.value = true;
  pipelineFormError.value = '';
  try {
    const payload = normalizePipelinePayload(pipelineForm.value);
    const response = await updateQueryPipelineConfig(payload);
    queryPipelineConfig.value = response;
    pipelineForm.value = {
      ...defaultPipelineForm(),
      ...normalizePipelinePayload(response || {})
    };
  } catch (error) {
    pipelineFormError.value = error.message || '流水线配置保存失败';
  } finally {
    isSavingPipeline.value = false;
  }
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
const deadTaskDocumentIds = computed(() => new Set(
  failedTasks.value
    .filter((task) => task.status === 'DEAD' && task.documentId)
    .map((task) => task.documentId)
));
const visibleDocuments = computed(() => {
  const knownIds = new Set(documents.value.map((document) => document.documentId));
  return [
    ...uploadingDocuments.value.filter((document) => (
      document.knowledgeBaseId === selectedKnowledgeBaseId.value && !knownIds.has(document.documentId)
    )),
    ...documents.value.filter((document) => !deadTaskDocumentIds.value.has(document.documentId))
  ];
});
const filteredDocuments = computed(() => {
  const keyword = documentKeyword.value.trim().toLowerCase();
  return visibleDocuments.value.filter((doc) => {
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
const filteredFailedTasks = computed(() => {
  const keyword = taskKeyword.value.trim().toLowerCase();
  return failedTasks.value.filter((task) => {
    const keywordMatched = !keyword || [
      task.taskId,
      task.documentId,
      task.fileName,
      task.lastError,
      task.mqMessageId,
      task.sourceRequestId
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
    const statusMatched = taskStatusFilter.value === 'ALL' || task.status === taskStatusFilter.value;
    return keywordMatched && statusMatched;
  }).slice().sort((left, right) => new Date(right.createdAt || 0) - new Date(left.createdAt || 0));
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
const messageTrendPoints = computed(() => {
  const points = dashboard.value?.messageTrendPoints;
  return Array.isArray(points) ? points : [];
});
const dashboardTrendSeries = computed(() => {
  const series = dashboard.value?.dashboardTrendSeries;
  if (Array.isArray(series) && series.length > 0) {
    return series;
  }
  const fallbackPoints = messageTrendPoints.value.map((point) => ({
    label: point.label || '-',
    value: point.messageCount || 0
  }));
  return [{
    type: 'message',
    title: '消息趋势',
    summaryLabel: '消息数',
    unit: '条',
    color: '#4C4F69',
    summaryValue: fallbackPoints.reduce((total, point) => total + point.value, 0),
    thresholds: [],
    points: fallbackPoints
  }];
});
const activeDashboardTrend = computed(() => {
  return dashboardTrendSeries.value.find((series) => series.type === activeTrendType.value)
    || dashboardTrendSeries.value[0]
    || null;
});
const activeTrendPoints = computed(() => {
  const points = activeDashboardTrend.value?.points;
  return Array.isArray(points) ? points : [];
});
const activeTrendThresholds = computed(() => {
  const thresholds = activeDashboardTrend.value?.thresholds;
  return Array.isArray(thresholds) ? thresholds : [];
});
const activeTrendSummaryValue = computed(() => {
  const value = activeDashboardTrend.value?.summaryValue;
  return Number.isFinite(Number(value)) ? Number(value) : 0;
});
const activeTrendOptionIndex = computed(() => {
  return Math.max(0, trendTypeOptions.findIndex((option) => option.type === activeTrendType.value));
});
const dashboardTrendRangeIndex = computed(() => messageTrendRange.value === 'month' ? 1 : 0);

onMounted(async () => {
  window.addEventListener('popstate', handleRouteChange);
  window.addEventListener('resize', resizeDashboardTrendChart);
  knowledgePollTimer = window.setInterval(pollProcessingDocuments, 3000);
  await Promise.all([loadDashboard(), loadKnowledge(), loadFailedTasks(), loadQueryAdmin()]);
  await runAdminReveal();
  await renderDashboardTrendChart();
});

onUnmounted(() => {
  window.removeEventListener('popstate', handleRouteChange);
  window.removeEventListener('resize', resizeDashboardTrendChart);
  stopAdminMotion?.();
  disposeDashboardTrendChart();
  if (knowledgePollTimer) {
    window.clearInterval(knowledgePollTimer);
  }
});

watch([activeTrendPoints, activeModule, activeTrendType], async () => {
  await renderDashboardTrendChart();
}, { deep: true });

watch(dashboardTrendSeries, (series) => {
  if (series.length > 0 && !series.some((item) => item.type === activeTrendType.value)) {
    activeTrendType.value = series[0].type;
  }
}, { deep: true });

async function runAdminReveal() {
  await nextTick();
  stopAdminMotion?.();
  stopAdminMotion = createQuietReveal(adminMain.value, {
    scroller: adminMain.value
  });
}

function parseAdminRoute() {
  const segments = window.location.pathname.split('/').filter(Boolean);
  if (segments[0] !== 'admin') {
    return { module: 'dashboard', view: 'dashboard' };
  }
  if (segments[1] === 'tasks') {
    return { module: 'tasks', view: 'failed-tasks' };
  }
  if (segments[1] === 'mappings') {
    return { module: 'mappings', view: 'mappings' };
  }
  if (segments[1] === 'pipeline') {
    return { module: 'pipeline', view: 'pipeline' };
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
  if (activeModule.value === 'tasks') {
    await loadFailedTasks();
  }
  if (activeModule.value === 'mappings') {
    await loadTerminologyMappings();
  }
  if (activeModule.value === 'pipeline') {
    await loadPipelineConfig();
  }
  await runAdminReveal();
  await renderDashboardTrendChart();
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
    dashboard.value = await fetchAdminDashboard({ messageRange: messageTrendRange.value });
  } catch (error) {
    handleAdminError(error);
  } finally {
    isLoadingDashboard.value = false;
    await renderDashboardTrendChart();
  }
}

async function setMessageTrendRange(value) {
  if (messageTrendRange.value === value || isLoadingDashboard.value) {
    return;
  }
  messageTrendRange.value = value;
  await loadDashboard();
}

function setActiveTrendType(value) {
  if (activeTrendType.value === value) {
    return;
  }
  activeTrendType.value = value;
}

async function renderDashboardTrendChart() {
  await nextTick();
  if (activeModule.value !== 'dashboard' || !dashboardTrendChart.value) {
    disposeDashboardTrendChart();
    return;
  }
  if (!dashboardChart) {
    dashboardChart = echarts.init(dashboardTrendChart.value, null, { renderer: 'canvas' });
  }

  const trend = activeDashboardTrend.value;
  const points = activeTrendPoints.value;
  const labels = points.map((point) => point.label || '-');
  const trendValues = points.map((point) => Number(point.value || 0));
  const thresholdValues = activeTrendThresholds.value.map((threshold) => Number(threshold.value || 0));
  const maxTrendValue = Math.max(...trendValues, ...thresholdValues, 0);
  const yAxisMax = Math.max(5, Math.ceil(maxTrendValue * 1.18));
  const trendColor = trend?.color || '#4C4F69';

  dashboardChart.setOption({
    backgroundColor: 'transparent',
    animation: false,
    color: [trendColor],
    textStyle: {
      fontFamily: '"Cascadia Mono", "Microsoft YaHei", Consolas, monospace',
      color: '#303446'
    },
    tooltip: {
      trigger: 'axis',
      borderWidth: 1,
      borderColor: 'rgba(76, 79, 105, 0.14)',
      backgroundColor: 'rgba(255, 255, 255, 0.98)',
      padding: [10, 12],
      textStyle: {
        color: '#303446',
        fontFamily: '"Cascadia Mono", "Microsoft YaHei", Consolas, monospace',
        fontSize: 12
      },
      axisPointer: {
        type: 'line',
        lineStyle: {
          color: 'rgba(76, 79, 105, 0.22)',
          width: 1
        }
      },
      formatter(params) {
        const index = params?.[0]?.dataIndex ?? 0;
        const point = points[index] || {};
        return [
          `<strong>${point.label || ''}</strong>`,
          `${trend?.summaryLabel || '数值'}: ${formatTrendValue(point.value || 0, trend?.unit)}`
        ].join('<br/>');
      }
    },
    legend: { show: false },
    grid: {
      top: 28,
      right: 28,
      bottom: 30,
      left: 42,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisTick: { show: false },
      axisLine: {
        lineStyle: { color: 'rgba(76, 79, 105, 0.12)' }
      },
      axisLabel: {
        color: 'rgba(48, 52, 70, 0.52)',
        fontSize: 12,
        interval: 0
      }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: yAxisMax,
      minInterval: 1,
      splitLine: {
        lineStyle: {
          color: 'rgba(76, 79, 105, 0.12)',
          type: 'dashed'
        }
      },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: 'rgba(48, 52, 70, 0.52)',
        fontSize: 12,
        formatter(value) {
          return formatTrendAxisValue(value, trend?.unit);
        }
      }
    },
    series: [
      createTrendSeries(trend?.summaryLabel || '趋势', trendValues, trend)
    ]
  }, true);
  dashboardChart.resize();
  renderDashboardTrendOverlay(labels, trendValues, trendColor);
}

async function renderDashboardTrendOverlay(labels, data, color = '#4C4F69') {
  await nextTick();
  if (!dashboardChart || !dashboardTrendShell.value || !dashboardTrendOverlay.value || !data.length) {
    return;
  }
  window.cancelAnimationFrame(dashboardTrendAnimationFrame);
  const shellRect = dashboardTrendShell.value.getBoundingClientRect();
  const svg = dashboardTrendOverlay.value;
  svg.setAttribute('viewBox', `0 0 ${shellRect.width} ${shellRect.height}`);
  svg.innerHTML = '';

  const pixelPoints = data.map((value, index) => {
    const [x, y] = dashboardChart.convertToPixel({ xAxisIndex: 0, yAxisIndex: 0 }, [labels[index], value]);
    return { x, y, value };
  });
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', createSmoothPath(pixelPoints));
  path.setAttribute('class', 'dashboard-trend-svg-line');
  path.style.stroke = color;
  svg.appendChild(path);

  const circles = pixelPoints.map((point) => {
    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    circle.setAttribute('cx', point.x);
    circle.setAttribute('cy', point.y);
    circle.setAttribute('r', '0');
    circle.setAttribute('class', 'dashboard-trend-svg-point');
    circle.style.stroke = color;
    svg.appendChild(circle);
    return circle;
  });

  const pathLength = path.getTotalLength();
  path.style.strokeDasharray = String(pathLength);
  path.style.strokeDashoffset = String(pathLength);
  const startedAt = window.performance.now();
  const lineDuration = 1680;
  const pointDuration = 180;
  const pointRadius = 3.5;

  function step(now) {
    if (!dashboardTrendOverlay.value) {
      return;
    }
    const elapsed = now - startedAt;
    const lineProgress = easeInOutCubic(Math.min(1, elapsed / lineDuration));
    path.style.strokeDashoffset = String(pathLength * (1 - lineProgress));
    circles.forEach((circle, index) => {
      const position = circles.length <= 1 ? 1 : index / (circles.length - 1);
      const pointStart = position * Math.max(0, lineDuration - pointDuration);
      const pointProgress = Math.max(0, Math.min(1, (elapsed - pointStart) / pointDuration));
      circle.setAttribute('r', String(pointRadius * easeOutCubic(pointProgress)));
    });

    if (elapsed < lineDuration + pointDuration) {
      dashboardTrendAnimationFrame = window.requestAnimationFrame(step);
    }
  }

  dashboardTrendAnimationFrame = window.requestAnimationFrame(step);
}

function createSmoothPath(points) {
  if (!points.length) {
    return '';
  }
  if (points.length === 1) {
    const point = points[0];
    return `M ${point.x} ${point.y}`;
  }
  return points.reduce((path, point, index) => {
    if (index === 0) {
      return `M ${point.x} ${point.y}`;
    }
    const previous = points[index - 1];
    const distance = point.x - previous.x;
    const controlOne = {
      x: previous.x + distance * 0.42,
      y: previous.y
    };
    const controlTwo = {
      x: point.x - distance * 0.42,
      y: point.y
    };
    return `${path} C ${controlOne.x} ${controlOne.y}, ${controlTwo.x} ${controlTwo.y}, ${point.x} ${point.y}`;
  }, '');
}

function easeOutCubic(value) {
  return 1 - Math.pow(1 - value, 3);
}

function easeInOutCubic(value) {
  if (value <= 0) {
    return 0;
  }
  if (value >= 1) {
    return 1;
  }
  return value < 0.5
    ? 4 * value * value * value
    : 1 - Math.pow(-2 * value + 2, 3) / 2;
}

function createTrendSeries(name, data, trend) {
  const color = trend?.color || '#4C4F69';
  const thresholds = Array.isArray(trend?.thresholds) ? trend.thresholds : [];
  return {
    name,
    type: 'line',
    data,
    smooth: true,
    showSymbol: true,
    symbol: 'circle',
    symbolSize: 10,
    connectNulls: false,
    lineStyle: {
      width: 3,
      color,
      opacity: 0,
      cap: 'round',
      join: 'round'
    },
    itemStyle: {
      color: '#ffffff',
      opacity: 0,
      borderWidth: 2,
      borderColor: color
    },
    markLine: thresholds.length ? {
      symbol: 'none',
      silent: true,
      data: thresholds.map((threshold) => ({
        name: threshold.label,
        yAxis: threshold.value,
        label: {
          formatter: threshold.label,
          position: 'insideEndTop',
          color: threshold.color || '#4C4F69',
          fontSize: 12,
          fontWeight: 700
        },
        lineStyle: {
          color: threshold.color || '#4C4F69',
          type: 'dashed',
          width: 1.5
        }
      }))
    } : undefined,
    emphasis: {
      focus: 'series',
      lineStyle: {
        width: 3
      }
    }
  };
}

function resizeDashboardTrendChart() {
  dashboardChart?.resize();
  if (dashboardChart) {
    const trend = activeDashboardTrend.value;
    const points = activeTrendPoints.value;
    renderDashboardTrendOverlay(
      points.map((point) => point.label || '-'),
      points.map((point) => Number(point.value || 0)),
      trend?.color || '#4C4F69'
    );
  }
}

function disposeDashboardTrendChart() {
  window.cancelAnimationFrame(dashboardTrendAnimationFrame);
  if (dashboardTrendOverlay.value) {
    dashboardTrendOverlay.value.innerHTML = '';
  }
  dashboardChart?.dispose();
  dashboardChart = null;
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

async function loadFailedTasks() {
  isLoadingTasks.value = true;
  try {
    const response = await fetchFailedIngestionTasks();
    failedTasks.value = Array.isArray(response) ? response : [];
  } catch (error) {
    handleAdminError(error);
  } finally {
    isLoadingTasks.value = false;
  }
}

async function loadQueryAdmin() {
  await Promise.all([loadTerminologyMappings(), loadPipelineConfig()]);
}

async function loadTerminologyMappings() {
  isLoadingMappings.value = true;
  try {
    const response = await fetchTerminologyMappings();
    terminologyMappings.value = Array.isArray(response) ? response : [];
  } catch (error) {
    handleAdminError(error);
  } finally {
    isLoadingMappings.value = false;
  }
}

async function loadPipelineConfig() {
  isLoadingPipeline.value = true;
  try {
    const response = await fetchQueryPipelineConfig();
    queryPipelineConfig.value = response;
    pipelineForm.value = {
      ...defaultPipelineForm(),
      ...normalizePipelinePayload(response || {})
    };
  } catch (error) {
    handleAdminError(error);
  } finally {
    isLoadingPipeline.value = false;
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
    if (activeModule.value === 'tasks') {
      await loadFailedTasks();
      return;
    }
    if (activeModule.value === 'mappings') {
      await loadTerminologyMappings();
      return;
    }
    if (activeModule.value === 'pipeline') {
      await loadPipelineConfig();
      return;
    }
    await loadKnowledge();
  } finally {
    window.setTimeout(() => {
      isRefreshing.value = false;
    }, 260);
  }
}

async function pollProcessingDocuments() {
  if (
    activeModule.value !== 'knowledge'
    || !visibleDocuments.value.some((document) => isBusyDocumentStatus(document.status))
    || isLoadingKnowledge.value
    || isRefreshing.value
  ) {
    return;
  }
  try {
    await hydrateKnowledgeRoute();
  } catch (_error) {
    // 轮询失败时保留当前页面状态，用户手动刷新时再展示错误。
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
  resetIngestionForm();
  isIngestionModalOpen.value = true;
}

function closeIngestionModal() {
  ingestionFormError.value = '';
  isIngesting.value = false;
  isIngestionModalOpen.value = false;
  resetIngestionForm();
}

function resetIngestionForm() {
  selectedFile.value = null;
  urlForm.value = { url: '', fileName: '' };
  ingestionMode.value = 'upload';
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
    if (ingestionMode.value === 'upload') {
      submitUploadInBackground(selectedKnowledgeBaseId.value, selectedFile.value);
      closeIngestionModal();
      return;
    } else {
      await ingestKnowledgeUrl(selectedKnowledgeBaseId.value, {
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

function submitUploadInBackground(knowledgeBaseId, file) {
  const optimisticDocument = createUploadingDocument(knowledgeBaseId, file);
  uploadingDocuments.value = [optimisticDocument, ...uploadingDocuments.value];
  uploadKnowledgeDocument(knowledgeBaseId, {
    file
  })
    .then(async () => {
      uploadingDocuments.value = uploadingDocuments.value.filter((document) => document.documentId !== optimisticDocument.documentId);
      await loadKnowledge();
    })
    .catch((error) => {
      uploadingDocuments.value = uploadingDocuments.value.filter((document) => document.documentId !== optimisticDocument.documentId);
      if (isSessionError(error)) {
        emit('session-expired');
        return;
      }
      adminError.value = error.message || '文件上传失败';
      loadKnowledge();
    });
}

function createUploadingDocument(knowledgeBaseId, file) {
  const now = new Date().toISOString();
  return {
    documentId: `uploading-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    knowledgeBaseId,
    fileName: file?.name || 'uploaded-document',
    sourceType: 'UPLOAD',
    sourceUrl: null,
    contentType: file?.type || '',
    originalSizeBytes: file?.size || 0,
    status: 'UPLOADING',
    textCharCount: 0,
    chunkCount: 0,
    chunkStrategy: 'RECURSIVE',
    chunkSize: 1000,
    chunkOverlap: 150,
    maxChunks: 200,
    textExtractedAt: null,
    parseDurationMs: 0,
    chunkDurationMs: 0,
    embeddingDurationMs: 0,
    otherDurationMs: 0,
    totalDurationMs: 0,
    createdAt: now,
    updatedAt: now,
    errorMessage: null
  };
}

async function openDocumentChunks(document) {
  if (!canOpenDocumentChunks(document)) {
    return;
  }
  await navigateTo(`/admin/knowledge/${selectedKnowledgeBaseId.value}/docs/${document.documentId}`);
}

function openRechunkModal(targetDocument) {
  if (!canRechunkDocument(targetDocument)) {
    return;
  }
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
    await navigateTo(`/admin/knowledge/${selectedKnowledgeBaseId.value}`);
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
  if (isBusyDocumentStatus(document?.status)) {
    return;
  }
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
  if (!selectedDocumentId.value || isRebuildingVectors.value || isSelectedDocumentProcessing.value) {
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

async function retryFailedTask(task) {
  if (!task?.taskId || retryingTaskId.value || deletingTaskId.value) {
    return;
  }
  retryingTaskId.value = task.taskId;
  adminError.value = '';
  try {
    await retryIngestionTask(task.taskId);
    await loadFailedTasks();
    window.setTimeout(() => {
      if (activeModule.value === 'tasks') {
        loadFailedTasks();
      }
    }, 1200);
    if (activeModule.value === 'knowledge') {
      await loadKnowledge();
    }
  } catch (error) {
    handleAdminError(error);
  } finally {
    retryingTaskId.value = '';
  }
}

function deleteFailedTask(task) {
  if (!task?.taskId || deletingTaskId.value || retryingTaskId.value) {
    return;
  }
  adminError.value = '';
  deleteTaskError.value = '';
  deleteTaskDialog.value = {
    open: true,
    task
  };
}

function closeDeleteTaskDialog() {
  if (deletingTaskId.value) {
    return;
  }
  deleteTaskDialog.value = {
    open: false,
    task: null
  };
  deleteTaskError.value = '';
}

async function confirmDeleteTask() {
  const target = deleteTaskDialog.value.task;
  if (!target?.taskId || deletingTaskId.value) {
    return;
  }
  deletingTaskId.value = target.taskId;
  deleteTaskError.value = '';
  try {
    await deleteIngestionTask(target.taskId);
    deleteTaskDialog.value = {
      open: false,
      task: null
    };
    await loadFailedTasks();
  } catch (error) {
    if (isSessionError(error)) {
      emit('session-expired');
      return;
    }
    deleteTaskError.value = error.message || '失败任务删除失败';
  } finally {
    deletingTaskId.value = '';
  }
}

async function setAllChunksEnabled(enabled) {
  if (!selectedDocumentId.value || !canMutateSelectedChunks.value) {
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
  if (ids.length === 0 || !canMutateSelectedChunks.value) {
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
  if (!canMutateSelectedChunks.value) {
    return;
  }
  try {
    const updated = await updateChunkEnabled(chunk.chunkId, !chunk.enabled);
    chunks.value = chunks.value.map((item) => item.chunkId === updated.chunkId ? updated : item);
  } catch (error) {
    handleAdminError(error);
  }
}

function removeChunk(chunk) {
  if (!canMutateSelectedChunks.value) {
    return;
  }
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
  if (!target?.chunkId || isDeletingChunk.value || !canMutateSelectedChunks.value) {
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
  if (!canMutateSelectedChunks.value) {
    return;
  }
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
  if (!editingChunk.value?.chunkId || isUpdatingChunk.value || !canMutateSelectedChunks.value) {
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

function formatTrendValue(value, unit) {
  const numberValue = Number(value || 0);
  if (unit === '毫秒') {
    return formatDuration(numberValue);
  }
  return formatNumber(numberValue);
}

function formatTrendAxisValue(value, unit) {
  const numberValue = Number(value || 0);
  if (unit === '毫秒') {
    if (numberValue >= 1000) {
      const seconds = numberValue / 1000;
      return `${seconds >= 10 ? seconds.toFixed(0) : seconds.toFixed(1)}s`;
    }
    return `${Math.round(numberValue)}ms`;
  }
  return formatNumber(Math.round(numberValue));
}

function metricText(value, fallback = '待接入') {
  return value === null || value === undefined ? fallback : formatNumber(value);
}

function sourceText(document) {
  return document.sourceType === 'URL' ? 'URL' : 'Local File';
}

function taskActionText(action) {
  if (action === 'REBUILD_VECTORS') {
    return '重建向量';
  }
  return '分块';
}

function taskStatusText(status) {
  if (status === 'DEAD') {
    return 'dead';
  }
  if (status === 'FAILED') {
    return 'failed';
  }
  if (status === 'RETRYING') {
    return 'retrying';
  }
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'RUNNING') {
    return 'running';
  }
  return 'pending';
}

function taskStatusClass(status) {
  if (status === 'DEAD' || status === 'FAILED') {
    return 'danger';
  }
  if (status === 'COMPLETED') {
    return 'success';
  }
  if (status === 'RETRYING' || status === 'RUNNING') {
    return 'pending';
  }
  return 'muted';
}

function taskOptionsText(task) {
  if (task.action === 'REBUILD_VECTORS') {
    return '沿用当前分块';
  }
  if (task.strategy === 'RECURSIVE') {
    return `${task.strategy.toLowerCase()} / ${task.chunkSize}-${task.chunkOverlap} / ${task.maxChunks}`;
  }
  return task.strategy ? task.strategy.toLowerCase() : '-';
}

function canRetryTask(task) {
  return task?.status === 'FAILED';
}

function taskDetailRows(task) {
  if (!task) {
    return [];
  }
  return [
    ['任务编号', task.taskId],
    ['文档编号', task.documentId],
    ['文档状态', task.documentStatus],
    ['动作', taskActionText(task.action)],
    ['状态', taskStatusText(task.status)],
    ['分块参数', taskOptionsText(task)],
    ['重试次数', `${task.retryCount} / ${task.maxRetries}`],
    ['最近失败', formatDate(task.lastFailedAt || task.updatedAt)],
    ['开始时间', task.lastStartedAt ? formatDate(task.lastStartedAt) : '-'],
    ['创建时间', formatDate(task.createdAt)],
    ['更新时间', formatDate(task.updatedAt)],
    ['MQ MessageId', task.mqMessageId || '-'],
    ['Source RequestId', task.sourceRequestId || '-'],
    ['错误原因', task.lastError || '-']
  ];
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
  if (status === 'UPLOADED') {
    return 'muted';
  }
  if (status === 'UPLOADING') {
    return 'pending';
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
  if (status === 'UPLOADED') {
    return 'uploaded';
  }
  if (status === 'UPLOADING') {
    return 'uploading';
  }
  return 'processing';
}

function isBusyDocumentStatus(status) {
  return status === 'UPLOADING' || status === 'PROCESSING';
}

function isFailedDocumentStatus(status) {
  return status === 'FAILED';
}

function shouldShowDocumentChunkAction(document) {
  return !isFailedDocumentStatus(document?.status);
}

function canRechunkDocument(document) {
  return document
    && shouldShowDocumentChunkAction(document)
    && !isBusyDocumentStatus(document.status);
}

function canOpenDocumentChunks(document) {
  return document
    && !isFailedDocumentStatus(document.status)
    && document.status !== 'UPLOADING';
}

function canViewDocumentChunks(document) {
  return canOpenDocumentChunks(document) && Number(document.chunkCount || 0) > 0;
}

function documentChunkActionLabel(document) {
  if (document.status === 'UPLOADING') {
    return '上传中...';
  }
  if (document.status === 'PROCESSING') {
    return '处理中...';
  }
  if (document.status === 'COMPLETED') {
    return '重新分块';
  }
  return '分块';
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
      <button class="admin-rail-button" type="button" :class="{ active: activeModule === 'tasks' }" data-tooltip="失败任务" @click="navigateTo('/admin/tasks/failed')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 5h16" />
          <path d="M4 12h10" />
          <path d="M4 19h7" />
          <path d="M17 15l4 4" />
          <path d="M21 15l-4 4" />
        </svg>
      </button>
      <button class="admin-rail-button" type="button" :class="{ active: activeModule === 'mappings' }" data-tooltip="关键词映射" @click="navigateTo('/admin/mappings')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 7h7" />
          <path d="M4 17h7" />
          <path d="M15 7h5" />
          <path d="M15 17h5" />
          <path d="M11 7l4 10" />
        </svg>
      </button>
      <button class="admin-rail-button" type="button" :class="{ active: activeModule === 'pipeline' }" data-tooltip="流水线配置" @click="navigateTo('/admin/pipeline')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 6h5" />
          <path d="M15 6h5" />
          <path d="M9 6h6" />
          <path d="M4 18h5" />
          <path d="M15 18h5" />
          <path d="M12 9v6" />
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
        <button type="button" :class="{ active: activeModule === 'tasks' }" @click="navigateTo('/admin/tasks/failed')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 5h16" />
            <path d="M4 12h10" />
            <path d="M4 19h7" />
            <path d="M17 15l4 4" />
            <path d="M21 15l-4 4" />
          </svg>
          <span>失败任务</span>
        </button>
        <button type="button" :class="{ active: activeModule === 'mappings' }" @click="navigateTo('/admin/mappings')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 7h7" />
            <path d="M4 17h7" />
            <path d="M15 7h5" />
            <path d="M15 17h5" />
            <path d="M11 7l4 10" />
          </svg>
          <span>关键词映射</span>
        </button>
        <button type="button" :class="{ active: activeModule === 'pipeline' }" @click="navigateTo('/admin/pipeline')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 6h5" />
            <path d="M15 6h5" />
            <path d="M9 6h6" />
            <path d="M4 18h5" />
            <path d="M15 18h5" />
            <path d="M12 9v6" />
          </svg>
          <span>流水线配置</span>
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

    <section ref="adminMain" class="admin-main">
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
              <svg v-else-if="currentHeader.icon === 'tasks'" viewBox="0 0 24 24">
                <path d="M4 5h16" />
                <path d="M4 12h10" />
                <path d="M4 19h7" />
                <path d="M17 15l4 4" />
                <path d="M21 15l-4 4" />
              </svg>
              <svg v-else-if="currentHeader.icon === 'mappings'" viewBox="0 0 24 24">
                <path d="M4 7h7" />
                <path d="M4 17h7" />
                <path d="M15 7h5" />
                <path d="M15 17h5" />
                <path d="M11 7l4 10" />
              </svg>
              <svg v-else-if="currentHeader.icon === 'pipeline'" viewBox="0 0 24 24">
                <path d="M4 6h5" />
                <path d="M15 6h5" />
                <path d="M9 6h6" />
                <path d="M4 18h5" />
                <path d="M15 18h5" />
                <path d="M12 9v6" />
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

        <article class="dashboard-trend-card">
          <header class="dashboard-trend-header">
            <div class="dashboard-trend-heading">
              <div class="dashboard-trend-title">
                <strong>趋势分析</strong>
                <span aria-label="趋势分析说明">?</span>
              </div>
              <div
                class="dashboard-trend-actions dashboard-trend-type-switch"
                :style="{ '--trend-index': activeTrendOptionIndex }"
                role="group"
                aria-label="趋势类型"
              >
                <span class="dashboard-trend-indicator" aria-hidden="true"></span>
                <button
                  v-for="option in trendTypeOptions"
                  :key="option.type"
                  type="button"
                  :class="{ active: activeDashboardTrend?.type === option.type }"
                  :disabled="isLoadingDashboard || !dashboardTrendSeries.some((series) => series.type === option.type)"
                  @click="setActiveTrendType(option.type)"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>
            <div
              class="dashboard-trend-actions dashboard-trend-range-switch"
              :style="{ '--trend-index': dashboardTrendRangeIndex }"
              role="group"
              aria-label="趋势范围"
            >
              <span class="dashboard-trend-indicator" aria-hidden="true"></span>
              <button
                type="button"
                :class="{ active: messageTrendRange === 'day' }"
                :disabled="isLoadingDashboard"
                @click="setMessageTrendRange('day')"
              >
                24小时
              </button>
              <button
                type="button"
                :class="{ active: messageTrendRange === 'month' }"
                :disabled="isLoadingDashboard"
                @click="setMessageTrendRange('month')"
              >
                本月
              </button>
            </div>
          </header>
          <div class="dashboard-trend-summary" :aria-label="`${activeDashboardTrend?.summaryLabel || '趋势'}统计`">
            <span class="dashboard-trend-summary-card">
              <i :style="{ backgroundColor: activeDashboardTrend?.color || '#4C4F69' }"></i>
              <span class="dashboard-trend-summary-copy">
                <b>{{ activeDashboardTrend?.summaryLabel || '趋势数' }}：{{ formatTrendValue(activeTrendSummaryValue, activeDashboardTrend?.unit) }}</b>
                <small>单位：{{ activeDashboardTrend?.unit || '-' }}</small>
              </span>
            </span>
          </div>
          <div
            v-if="activeTrendPoints.length"
            ref="dashboardTrendShell"
            class="dashboard-trend-chart"
            :aria-label="`${activeDashboardTrend?.title || '趋势'}折线图`"
          >
            <div ref="dashboardTrendChart" class="dashboard-trend-chart-canvas"></div>
            <svg ref="dashboardTrendOverlay" class="dashboard-trend-overlay" aria-hidden="true"></svg>
          </div>
          <p v-else class="dashboard-trend-empty">
            {{ isLoadingDashboard ? '趋势数据加载中...' : '暂无趋势数据' }}
          </p>
        </article>
      </section>

      <section v-else-if="activeModule === 'tasks'" class="admin-section kc-content">
        <div class="kc-metric-grid knowledge-metrics">
          <article class="kc-metric-card icon-card danger">
            <span class="kc-metric-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 5h16" />
                <path d="M4 12h10" />
                <path d="M4 19h7" />
                <path d="M17 15l4 4" />
                <path d="M21 15l-4 4" />
              </svg>
            </span>
            <span>失败任务</span>
            <strong>{{ isLoadingTasks ? '...' : metricText(failedTasks.length, '0') }}</strong>
          </article>
          <article class="kc-metric-card icon-card">
            <span class="kc-metric-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 6v6l4 2" />
                <circle cx="12" cy="12" r="9" />
              </svg>
            </span>
            <span>死信任务</span>
            <strong>{{ failedTasks.filter((task) => task.status === 'DEAD').length }}</strong>
          </article>
          <article class="kc-metric-card icon-card">
            <span class="kc-metric-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M20 6v5h-5" />
                <path d="M4 18v-5h5" />
                <path d="M19 11a7 7 0 0 0-12.2-4.2L4 9" />
                <path d="M5 13a7 7 0 0 0 12.2 4.2L20 15" />
              </svg>
            </span>
            <span>可手动重试</span>
            <strong>{{ failedTasks.filter((task) => task.status === 'FAILED').length }}</strong>
          </article>
        </div>

        <section class="kc-table-card">
          <div class="kc-card-toolbar">
            <div>
              <strong>失败任务列表</strong>
              <small>共 {{ filteredFailedTasks.length }} 条</small>
            </div>
            <div class="kc-toolbar-actions">
              <input v-model="taskKeyword" type="search" placeholder="搜索文档、任务或错误原因" />
              <div class="kc-select-menu" :class="{ open: isTaskStatusMenuOpen }">
                <button type="button" class="kc-select-trigger" @click="toggleTaskStatusMenu">
                  <span>{{ taskStatusFilterText(taskStatusFilter) }}</span>
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
                </button>
                <div v-if="isTaskStatusMenuOpen" class="kc-select-options">
                  <button type="button" :class="{ active: taskStatusFilter === 'ALL' }" @click="setTaskStatus('ALL')">全部状态</button>
                  <button type="button" :class="{ active: taskStatusFilter === 'FAILED' }" @click="setTaskStatus('FAILED')">failed</button>
                  <button type="button" :class="{ active: taskStatusFilter === 'DEAD' }" @click="setTaskStatus('DEAD')">dead</button>
                </div>
              </div>
            </div>
          </div>
          <div class="kc-table-head task-grid">
            <span>文档</span>
            <span>动作</span>
            <span>状态</span>
            <span>重试</span>
            <span>最近失败</span>
            <span>操作</span>
          </div>
          <div class="kc-table-body">
            <div v-for="task in filteredFailedTasks" :key="task.taskId" class="kc-table-row task-grid">
              <span class="kc-cell-tooltip" @mouseenter="showOverflowTooltip($event, task.fileName)" @mouseleave="clearOverflowTooltip">
                <span class="kc-tooltip-content">{{ task.fileName }}</span>
              </span>
              <span>{{ taskActionText(task.action) }}</span>
              <span class="kc-status" :class="taskStatusClass(task.status)">{{ taskStatusText(task.status) }}</span>
              <span>{{ task.retryCount }} / {{ task.maxRetries }}</span>
              <span>{{ formatDate(task.lastFailedAt || task.updatedAt) }}</span>
              <span class="kc-row-actions compact task-actions">
                <button type="button" @click="detailTask = task">详情</button>
                <button v-if="canRetryTask(task)" type="button" :disabled="retryingTaskId === task.taskId || deletingTaskId === task.taskId" @click="retryFailedTask(task)">
                  {{ retryingTaskId === task.taskId ? '投递中' : '重试' }}
                </button>
                <button type="button" class="danger" :disabled="retryingTaskId === task.taskId || deletingTaskId === task.taskId" @click="deleteFailedTask(task)">
                  {{ deletingTaskId === task.taskId ? '删除中' : '删除' }}
                </button>
              </span>
            </div>
          </div>
          <p v-if="!isLoadingTasks && filteredFailedTasks.length === 0" class="kc-empty">当前没有失败入库任务。</p>
        </section>
      </section>

      <section v-else-if="activeModule === 'mappings'" class="admin-section kc-content">
        <div class="kc-metric-grid knowledge-metrics">
          <article class="kc-metric-card icon-card">
            <span class="kc-metric-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 7h7" />
                <path d="M15 7h5" />
                <path d="M11 7l4 10" />
              </svg>
            </span>
            <span>映射数</span>
            <strong>{{ isLoadingMappings ? '...' : metricText(terminologyMappings.length) }}</strong>
          </article>
          <article class="kc-metric-card icon-card">
            <span class="kc-metric-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 12h14" />
                <path d="M12 5v14" />
              </svg>
            </span>
            <span>标准术语</span>
            <strong>{{ metricText(terminologyTermCount) }}</strong>
          </article>
          <article class="kc-metric-card icon-card">
            <span class="kc-metric-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M20 6L9 17l-5-5" />
              </svg>
            </span>
            <span>启用映射</span>
            <strong>{{ metricText(enabledMappingCount) }}</strong>
          </article>
        </div>

        <section class="kc-table-card">
          <div class="kc-card-toolbar">
            <div>
              <strong>关键词映射列表</strong>
              <small>共 {{ filteredTerminologyMappings.length }} 条</small>
            </div>
            <div class="kc-toolbar-actions">
              <input v-model="mappingKeyword" type="search" placeholder="搜索原始词、目标词或备注" />
              <button type="button" class="kc-primary-button" @click="openCreateMappingModal">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 5v14" />
                  <path d="M5 12h14" />
                </svg>
                <span>新增映射</span>
              </button>
            </div>
          </div>
          <div class="kc-table-head mapping-grid">
            <span>原始词</span>
            <span>目标词</span>
            <span>类型</span>
            <span>优先级</span>
            <span>状态</span>
            <span>备注</span>
            <span>更新时间</span>
            <span>操作</span>
          </div>
          <div class="kc-table-body">
            <div v-for="mapping in filteredTerminologyMappings" :key="mapping.aliasId" class="kc-table-row mapping-grid">
              <span class="kc-cell-tooltip" @mouseenter="showOverflowTooltip($event, mapping.aliasName)" @mouseleave="clearOverflowTooltip">
                <span class="kc-tooltip-content">{{ mapping.aliasName }}</span>
              </span>
              <span class="kc-tag">{{ mapping.canonicalName }}</span>
              <span>{{ mapping.termType || 'TECH' }}</span>
              <span>{{ mapping.priority ?? 0 }}</span>
              <span class="kc-status" :class="mapping.enabled ? 'success' : 'muted'">{{ mapping.enabled ? '启用' : '禁用' }}</span>
              <span class="kc-cell-tooltip" @mouseenter="showOverflowTooltip($event, mapping.description || '-')" @mouseleave="clearOverflowTooltip">
                <span class="kc-tooltip-content">{{ mapping.description || '-' }}</span>
              </span>
              <span>{{ formatDate(mapping.updatedAt || mapping.createdAt) }}</span>
              <span class="kc-row-actions compact">
                <button type="button" @click="openEditMappingModal(mapping)">编辑</button>
                <button type="button" @click="toggleMappingEnabled(mapping)">{{ mapping.enabled ? '禁用' : '启用' }}</button>
                <button type="button" class="danger" @click="openDeleteMappingDialog(mapping)">删除</button>
              </span>
            </div>
          </div>
          <p v-if="!isLoadingMappings && filteredTerminologyMappings.length === 0" class="kc-empty">还没有关键词映射。</p>
        </section>
      </section>

      <section v-else-if="activeModule === 'pipeline'" class="admin-section kc-content">
        <div class="pipeline-layout">
          <section class="pipeline-card">
            <header>
              <div>
                <strong>查询预处理策略</strong>
                <small>控制术语统一、LLM 改写和降级方式</small>
              </div>
              <span class="kc-status" :class="pipelineForm.llmRewriteEnabled ? 'success' : 'muted'">
                {{ pipelineForm.llmRewriteEnabled ? '改写开启' : '改写关闭' }}
              </span>
            </header>
            <form class="pipeline-form" @submit.prevent="submitPipelineConfig">
              <p v-if="pipelineFormError" class="kc-form-error">{{ pipelineFormError }}</p>
              <label class="pipeline-switch-row locked">
                <span>
                  <strong>术语统一</strong>
                  <small>始终先执行，使用 Redis 缓存后的术语表快照</small>
                </span>
                <input v-model="pipelineForm.terminologyEnabled" type="checkbox" disabled />
                <i class="pipeline-switch" aria-hidden="true"></i>
              </label>
              <label class="pipeline-switch-row">
                <span>
                  <strong>LLM 语义改写</strong>
                  <small>关闭后只保留术语统一和单问题兜底</small>
                </span>
                <input v-model="pipelineForm.llmRewriteEnabled" type="checkbox" />
                <i class="pipeline-switch" aria-hidden="true"></i>
              </label>
              <label class="pipeline-switch-row">
                <span>
                  <strong>规则拆分兜底</strong>
                  <small>只按问号、分号、换行等明确分隔符拆分</small>
                </span>
                <input v-model="pipelineForm.ruleSplitEnabled" type="checkbox" />
                <i class="pipeline-switch" aria-hidden="true"></i>
              </label>
              <div class="pipeline-field-grid">
                <label>
                  <span>降级策略</span>
                  <select v-model="pipelineForm.fallbackPolicy">
                    <option value="TERM_ONLY">只保留术语统一</option>
                    <option value="RULE_SPLIT">术语统一 + 规则拆分</option>
                    <option value="BYPASS">跳过预处理</option>
                  </select>
                </label>
                <label>
                  <span>改写超时 ms</span>
                  <input v-model.number="pipelineForm.rewriteTimeoutMs" type="number" min="500" max="30000" />
                </label>
                <label>
                  <span>最近上下文轮数</span>
                  <input v-model.number="pipelineForm.rewriteContextTurns" type="number" min="1" max="10" />
                </label>
              </div>
              <footer>
                <button type="button" class="kc-ghost-button" :disabled="isLoadingPipeline || isSavingPipeline" @click="loadPipelineConfig">恢复当前配置</button>
                <button type="submit" class="kc-primary-button" :disabled="isSavingPipeline || !isPipelineDirty">
                  {{ isSavingPipeline ? '保存中...' : '保存配置' }}
                </button>
              </footer>
            </form>
          </section>

          <section class="pipeline-card muted">
            <header>
              <div>
                <strong>当前降级链路</strong>
                <small>服务发生异常时按这里继续执行</small>
              </div>
            </header>
            <ol class="pipeline-flow-list">
              <li><span>1</span><strong>术语统一</strong><small>先根据关键词映射表生成 normalizedQuery</small></li>
              <li><span>2</span><strong>LLM 改写</strong><small>开启时读取摘要和最近 {{ pipelineForm.rewriteContextTurns }} 轮对话</small></li>
              <li><span>3</span><strong>容错解析</strong><small>解析 JSON，失败进入 fallbackPolicy</small></li>
              <li><span>4</span><strong>写入 ctx</strong><small>rewriteResult 交给意图识别阶段</small></li>
            </ol>
          </section>
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
                <div class="kc-select-menu" :class="{ open: isDocumentStatusMenuOpen }">
                  <button type="button" class="kc-select-trigger" @click="toggleDocumentStatusMenu">
                    <span>{{ documentStatusFilterText(documentStatusFilter) }}</span>
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
                  </button>
                  <div v-if="isDocumentStatusMenuOpen" class="kc-select-options">
                    <button type="button" :class="{ active: documentStatusFilter === 'ALL' }" @click="setDocumentStatus('ALL')">全部状态</button>
                    <button type="button" :class="{ active: documentStatusFilter === 'UPLOADING' }" @click="setDocumentStatus('UPLOADING')">uploading</button>
                    <button type="button" :class="{ active: documentStatusFilter === 'UPLOADED' }" @click="setDocumentStatus('UPLOADED')">uploaded</button>
                    <button type="button" :class="{ active: documentStatusFilter === 'COMPLETED' }" @click="setDocumentStatus('COMPLETED')">success</button>
                    <button type="button" :class="{ active: documentStatusFilter === 'PROCESSING' }" @click="setDocumentStatus('PROCESSING')">processing</button>
                    <button type="button" :class="{ active: documentStatusFilter === 'FAILED' }" @click="setDocumentStatus('FAILED')">failed</button>
                  </div>
                </div>
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
                  :disabled="!canOpenDocumentChunks(doc)"
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
                  <button
                    v-if="shouldShowDocumentChunkAction(doc)"
                    type="button"
                    :aria-label="documentChunkActionLabel(doc)"
                    :disabled="!canRechunkDocument(doc)"
                    @click.stop="openRechunkModal(doc)"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12a9 9 0 0 1-9 9" /><path d="M3 12a9 9 0 0 1 9-9" /><path d="M21 3v6h-6" /><path d="M3 21v-6h6" /></svg>
                    <span>{{ documentChunkActionLabel(doc) }}</span>
                  </button>
                  <button type="button" aria-label="分块详情" @click="detailDocument = doc">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 8h.01" /><path d="M11 12h1v4h1" /></svg>
                    <span>详情</span>
                  </button>
                  <button v-if="!isFailedDocumentStatus(doc.status)" type="button" aria-label="查看分块" :disabled="!canViewDocumentChunks(doc)" @click="openDocumentChunks(doc)">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h16v16H4z" /><path d="M8 8h8" /><path d="M8 12h8" /><path d="M8 16h5" /></svg>
                    <span>查看分块</span>
                  </button>
                  <button type="button" class="danger" aria-label="删除文档" :disabled="isBusyDocumentStatus(doc.status)" @click="removeDocument(doc)">
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
              <button type="button" class="kc-ghost-button" :disabled="isRebuildingVectors || isSelectedDocumentProcessing" @click="rebuildVectors">
                {{ isRebuildingVectors || isSelectedDocumentProcessing ? '处理中...' : '重建向量' }}
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
                  <button type="button" class="kc-select-trigger" @click="toggleChunkStatusMenu">
                    <span>{{ statusFilterText(chunkStatusFilter) }}</span>
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
                  </button>
                  <div v-if="isChunkStatusMenuOpen" class="kc-select-options">
                    <button type="button" :class="{ active: chunkStatusFilter === 'ALL' }" @click="setChunkStatus('ALL')">全部状态</button>
                    <button type="button" :class="{ active: chunkStatusFilter === 'ENABLED' }" @click="setChunkStatus('ENABLED')">启用</button>
                    <button type="button" :class="{ active: chunkStatusFilter === 'DISABLED' }" @click="setChunkStatus('DISABLED')">禁用</button>
                  </div>
                </div>
                <button type="button" class="kc-ghost-button" :disabled="selectedChunkIds.size === 0 || !canMutateSelectedChunks" @click="setSelectedChunksEnabled(true)">批量启用</button>
                <button type="button" class="kc-ghost-button" :disabled="selectedChunkIds.size === 0 || !canMutateSelectedChunks" @click="setSelectedChunksEnabled(false)">批量禁用</button>
                <button type="button" class="kc-ghost-button" :disabled="!canMutateSelectedChunks" @click="setAllChunksEnabled(true)">全量启用</button>
                <button type="button" class="kc-ghost-button" :disabled="!canMutateSelectedChunks" @click="setAllChunksEnabled(false)">全量禁用</button>
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
                  <button type="button" :disabled="!canMutateSelectedChunks" @click="openEditChunkModal(chunk)">修改</button>
                  <button type="button" :disabled="!canMutateSelectedChunks" @click="toggleChunk(chunk)">{{ chunk.enabled ? '禁用' : '启用' }}</button>
                  <button type="button" class="danger" :disabled="!canMutateSelectedChunks" @click="removeChunk(chunk)">删除</button>
                </span>
              </div>
            </div>
            <p v-if="filteredChunks.length === 0" class="kc-empty">这个文档还没有分块。</p>
          </section>
        </template>
      </section>
    </section>

    <div v-if="isMappingModalOpen" class="kc-modal-backdrop" @click.self="closeMappingModal">
      <section class="kc-modal">
        <header>
          <div>
            <h2>{{ editingMapping ? '编辑关键词映射' : '新增关键词映射' }}</h2>
            <p>用于查询改写前的术语统一</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" :disabled="isSavingMapping" @click="closeMappingModal">×</button>
        </header>
        <form class="kc-form" @submit.prevent="submitMappingForm">
          <p v-if="mappingFormError" class="kc-form-error">{{ mappingFormError }}</p>
          <label>
            <span>原始词</span>
            <input v-model="mappingForm.aliasName" type="text" placeholder="例如 网关 / gateway / kb" />
          </label>
          <label>
            <span>目标词</span>
            <input v-model="mappingForm.canonicalName" type="text" placeholder="例如 Gateway / RAG" />
          </label>
          <label>
            <span>术语类型</span>
            <select v-model="mappingForm.termType">
              <option value="TECH">TECH</option>
              <option value="MODULE">MODULE</option>
              <option value="CAPABILITY">CAPABILITY</option>
              <option value="BUSINESS">BUSINESS</option>
            </select>
          </label>
          <label>
            <span>优先级</span>
            <input v-model.number="mappingForm.priority" type="number" />
          </label>
          <label>
            <span>备注</span>
            <textarea v-model="mappingForm.description" rows="3" placeholder="可选，说明这个映射的使用场景"></textarea>
          </label>
          <label class="pipeline-switch-row compact">
            <span>
              <strong>启用映射</strong>
              <small>禁用后不会参与术语统一</small>
            </span>
            <input v-model="mappingForm.enabled" type="checkbox" />
            <i class="pipeline-switch" aria-hidden="true"></i>
          </label>
          <footer>
            <button type="button" class="kc-ghost-button" :disabled="isSavingMapping" @click="closeMappingModal">取消</button>
            <button type="submit" class="kc-primary-button" :disabled="!canSubmitMapping">
              {{ isSavingMapping ? '保存中...' : '保存映射' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <Teleport to="body">
      <Transition name="delete-dialog">
        <div
          v-if="deleteMappingDialog.open"
          class="delete-dialog-backdrop"
          @click.self="closeDeleteMappingDialog"
        >
          <section
            class="delete-dialog-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="mapping-delete-dialog-title"
            aria-describedby="mapping-delete-dialog-description"
          >
            <button
              type="button"
              class="delete-dialog-close"
              aria-label="关闭删除确认"
              :disabled="isDeletingMapping"
              @click="closeDeleteMappingDialog"
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
                </svg>
              </span>
            </div>

            <div class="delete-dialog-copy">
              <p class="delete-dialog-eyebrow">危险操作</p>
              <h2 id="mapping-delete-dialog-title">删除这个关键词映射？</h2>
              <p id="mapping-delete-dialog-description">
                删除后该原始词不会再被统一到目标词，后续查询改写会立即按新缓存重建。
              </p>
            </div>

            <div class="delete-dialog-target">
              <span>将被删除</span>
              <strong>{{ deleteMappingDialog.mapping?.aliasName }} -> {{ deleteMappingDialog.mapping?.canonicalName }}</strong>
            </div>

            <p v-if="deleteMappingError" class="delete-dialog-error">
              {{ deleteMappingError }}
            </p>

            <div class="delete-dialog-actions">
              <button
                type="button"
                class="delete-dialog-secondary"
                :disabled="isDeletingMapping"
                autofocus
                @click="closeDeleteMappingDialog"
              >
                先留着
              </button>
              <button
                type="button"
                class="delete-dialog-danger"
                :disabled="isDeletingMapping"
                @click="confirmDeleteMapping"
              >
                {{ isDeletingMapping ? '删除中...' : '删除映射' }}
              </button>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>

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

    <Teleport to="body">
      <Transition name="delete-dialog">
        <div
          v-if="deleteTaskDialog.open"
          class="delete-dialog-backdrop"
          @click.self="closeDeleteTaskDialog"
        >
          <section
            class="delete-dialog-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="task-delete-dialog-title"
            aria-describedby="task-delete-dialog-description"
          >
            <button
              type="button"
              class="delete-dialog-close"
              aria-label="关闭删除确认"
              :disabled="Boolean(deletingTaskId)"
              @click="closeDeleteTaskDialog"
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
              <h2 id="task-delete-dialog-title">删除这个失败任务？</h2>
              <p id="task-delete-dialog-description">
                这里只删除失败任务记录，不会删除原始文档和文档状态。
              </p>
            </div>

            <div class="delete-dialog-target">
              <span>将被删除</span>
              <strong>{{ deleteTaskDialog.task?.fileName || '这个失败任务' }}</strong>
            </div>

            <p class="delete-dialog-warning">
              删除后后台失败任务列表不再展示这条记录，文档仍可在文档管理中查看。
            </p>

            <p v-if="deleteTaskError" class="delete-dialog-error">
              {{ deleteTaskError }}
            </p>

            <div class="delete-dialog-actions">
              <button
                type="button"
                class="delete-dialog-secondary"
                :disabled="Boolean(deletingTaskId)"
                autofocus
                @click="closeDeleteTaskDialog"
              >
                先留着
              </button>
              <button
                type="button"
                class="delete-dialog-danger"
                :disabled="Boolean(deletingTaskId)"
                @click="confirmDeleteTask"
              >
                {{ deletingTaskId ? '删除中...' : '删除任务' }}
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

    <div v-if="detailTask" class="kc-modal-backdrop" @click.self="detailTask = null">
      <section class="kc-modal wide">
        <header>
          <div>
            <h2>失败任务详情</h2>
            <p>{{ detailTask.fileName }}</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="detailTask = null">×</button>
        </header>
        <div class="kc-detail-grid task-detail-grid">
          <template v-for="row in taskDetailRows(detailTask)" :key="row[0]">
            <span>{{ row[0] }}</span>
            <strong class="kc-detail-value">{{ row[1] }}</strong>
          </template>
        </div>
        <footer>
          <button type="button" class="kc-ghost-button" @click="detailTask = null">关闭</button>
        </footer>
      </section>
    </div>

    <div v-if="isIngestionModalOpen" class="kc-modal-backdrop" @click.self="closeIngestionModal">
      <section class="kc-modal wide">
        <header>
          <div>
            <h2>上传文档</h2>
            <p>{{ selectedKnowledgeBase?.name }}</p>
          </div>
          <button type="button" class="kc-icon-button" aria-label="关闭" @click="closeIngestionModal">×</button>
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
          <footer>
            <button type="button" class="kc-ghost-button" @click="closeIngestionModal">取消</button>
            <button type="submit" class="kc-primary-button" :disabled="!canIngest">
              {{ isIngesting ? '上传中...' : '上传到 RustFS' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="isRechunkModalOpen" class="kc-modal-backdrop" @click.self="closeRechunkModal">
      <section class="kc-modal">
        <header>
          <div>
            <h2>{{ rechunkDocument?.status === 'COMPLETED' ? '重新分块' : '分块' }}</h2>
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
    <Teleport to="body">
      <div
        v-if="floatingTooltip.visible"
        class="kc-floating-tooltip"
        :class="floatingTooltip.placement"
        :style="{ left: `${floatingTooltip.left}px`, top: `${floatingTooltip.top}px` }"
      >
        {{ floatingTooltip.text }}
      </div>
    </Teleport>
  </main>
</template>
