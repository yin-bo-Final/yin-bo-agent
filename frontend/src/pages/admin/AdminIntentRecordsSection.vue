<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { fetchIntentResolveRecords } from '../../api/adminApi';
import AdminDateTimeRangeInput from './AdminDateTimeRangeInput.vue';
import AdminDetailModal from './AdminDetailModal.vue';
import AdminFilterBar from './AdminFilterBar.vue';
import AdminJsonPreview from './AdminJsonPreview.vue';
import AdminSelect from './AdminSelect.vue';
import AdminTable from './AdminTable.vue';
import { formatDate, formatDuration, metricText } from './adminPageUtils';
import StatusBadge from './StatusBadge.vue';

const emit = defineEmits(['error']);

const outcomeFilterOptions = [
  { label: '全部结果', value: 'ALL' },
  { label: 'success', value: 'SUCCESS' },
  { label: 'fallback', value: 'FALLBACK' }
];
const ambiguousFilterOptions = [
  { label: '全部歧义', value: 'ALL' },
  { label: '有歧义', value: 'true' },
  { label: '无歧义', value: 'false' }
];

const records = ref([]);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const pages = ref(0);
const keyword = ref('');
const outcomeFilter = ref('ALL');
const ambiguousFilter = ref('ALL');
const startAt = ref('');
const endAt = ref('');
const isLoading = ref(false);
const selectedRecord = ref(null);
const badCaseExpectedNodeCode = ref('NONE');
const badCaseCopyState = ref('idle');

const intentRecordColumns = [
  '问题',
  '结果',
  '节点',
  '模型',
  '耗时',
  '时间',
  '操作'
];
const successCount = computed(() => records.value.filter((record) => record.outcome === 'SUCCESS').length);
const fallbackCount = computed(() => records.value.filter((record) => record.outcome === 'FALLBACK').length);
const ambiguousCount = computed(() => records.value.filter((record) => record.ambiguous).length);
const selectedRecordSubtitle = computed(() => {
  return selectedRecord.value ? `#${selectedRecord.value.id} · ${formatDate(selectedRecord.value.createdAt)}` : '';
});
const badCaseExpectedOptions = computed(() => {
  const values = arrayValue(selectedRecord.value?.selectedNodes)
    .map((node) => node?.nodeCode)
    .filter(Boolean);
  return ['NONE', ...new Set(values)];
});
const badCaseCsvLine = computed(() => {
  const question = selectedRecord.value?.originalQuery || queryPreview(selectedRecord.value || {});
  return `${csvCell(question)}|${csvCell(normalizeExpectedNodeCode(badCaseExpectedNodeCode.value))}`;
});

onMounted(loadRecords);

watch(selectedRecord, (record) => {
  badCaseCopyState.value = 'idle';
  badCaseExpectedNodeCode.value = defaultExpectedNodeCode(record);
});

defineExpose({
  reloadRecords: loadRecords
});

async function loadRecords() {
  if (isLoading.value) {
    return;
  }
  isLoading.value = true;
  try {
    const response = await fetchIntentResolveRecords({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim(),
      outcome: outcomeFilter.value,
      ambiguous: ambiguousFilter.value,
      startAt: startAt.value,
      endAt: endAt.value
    });
    records.value = Array.isArray(response?.records) ? response.records : [];
    total.value = Number(response?.total || 0);
    pages.value = Number(response?.pages || 0);
    page.value = Number(response?.page || page.value);
    pageSize.value = Number(response?.pageSize || pageSize.value);
  } catch (error) {
    emit('error', error);
  } finally {
    isLoading.value = false;
  }
}

async function applyFilters() {
  page.value = 1;
  await loadRecords();
}

async function resetFilters() {
  keyword.value = '';
  outcomeFilter.value = 'ALL';
  ambiguousFilter.value = 'ALL';
  startAt.value = '';
  endAt.value = '';
  page.value = 1;
  await loadRecords();
}

async function goToPage(nextPage) {
  const safePage = Math.max(1, Math.min(Number(nextPage || 1), pages.value || 1));
  if (safePage === page.value || isLoading.value) {
    return;
  }
  page.value = safePage;
  await loadRecords();
}

async function changePageSize(nextPageSize) {
  pageSize.value = Number(nextPageSize || pageSize.value);
  page.value = 1;
  await loadRecords();
}

function outcomeText(value) {
  if (value === 'SUCCESS') {
    return 'success';
  }
  if (value === 'FALLBACK') {
    return 'fallback';
  }
  return value ? value.toLowerCase() : 'unknown';
}

function outcomeClass(record) {
  if (record.outcome === 'SUCCESS') {
    return 'success';
  }
  if (record.outcome === 'FALLBACK') {
    return 'pending';
  }
  if (record.success === false) {
    return 'danger';
  }
  return 'muted';
}

function queryPreview(record) {
  return record.rewrittenQuery || record.normalizedQuery || record.originalQuery || '-';
}

function arrayValue(value) {
  return Array.isArray(value) ? value : [];
}

function selectedNodeLabel(node) {
  return node?.path || node?.nodeCode || '-';
}

function scoreText(value) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue.toFixed(2) : '-';
}

function defaultExpectedNodeCode(record) {
  const nodes = arrayValue(record?.selectedNodes);
  const ruleNode = nodes.find((node) => node?.source === 'RULE' && node.nodeCode);
  const firstNode = nodes.find((node) => node?.nodeCode);
  return ruleNode?.nodeCode || firstNode?.nodeCode || 'NONE';
}

function normalizeExpectedNodeCode(value) {
  const cleanValue = String(value || '').trim();
  return cleanValue || 'NONE';
}

function csvCell(value) {
  const text = String(value || '')
    .replace(/[\r\n]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (/[|"]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

function setBadCaseExpectedNodeCode(value) {
  badCaseExpectedNodeCode.value = value;
  badCaseCopyState.value = 'idle';
}

async function copyBadCaseSample() {
  try {
    await writeClipboard(badCaseCsvLine.value);
    badCaseCopyState.value = 'copied';
    window.setTimeout(() => {
      if (badCaseCopyState.value === 'copied') {
        badCaseCopyState.value = 'idle';
      }
    }, 1800);
  } catch (_error) {
    badCaseCopyState.value = 'failed';
  }
}

async function writeClipboard(content) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(content);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = content;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.left = '-9999px';
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand('copy');
  document.body.removeChild(textarea);
  if (!copied) {
    throw new Error('copy failed');
  }
}
</script>

<template>
  <section class="admin-section kc-content intent-records-module">
    <div class="kc-metric-grid knowledge-metrics">
      <article class="kc-metric-card icon-card">
        <span class="kc-metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 5h14" />
            <path d="M5 12h8" />
            <path d="M5 19h6" />
            <path d="M16 14l3 3" />
            <circle cx="15" cy="13" r="3" />
          </svg>
        </span>
        <span>记录总数</span>
        <strong>{{ isLoading ? '...' : metricText(total) }}</strong>
      </article>
      <article class="kc-metric-card icon-card good">
        <span class="kc-metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 6L9 17l-5-5" />
          </svg>
        </span>
        <span>本页成功</span>
        <strong>{{ metricText(successCount) }}</strong>
      </article>
      <article class="kc-metric-card icon-card warn">
        <span class="kc-metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 8v4" />
            <path d="M12 16h.01" />
            <circle cx="12" cy="12" r="9" />
          </svg>
        </span>
        <span>本页降级</span>
        <strong>{{ metricText(fallbackCount) }}</strong>
      </article>
      <article class="kc-metric-card icon-card">
        <span class="kc-metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 5v14" />
            <path d="M5 12h14" />
          </svg>
        </span>
        <span>本页歧义</span>
        <strong>{{ metricText(ambiguousCount) }}</strong>
      </article>
    </div>

    <AdminTable
      title="意图识别记录"
      description="查看每轮会话的改写结果、候选节点、最终 outcome 和耗时"
      card-class="intent-records-card"
      grid-class="intent-record-grid"
      empty-text="没有匹配的意图识别记录。"
      :columns="intentRecordColumns"
      :rows="records"
      :loading="isLoading"
      :page="page"
      :pages="pages"
      :page-size="pageSize"
      :total="total"
      @page-change="goToPage"
      @page-size-change="changePageSize"
    >
      <template #actions>
        <AdminFilterBar @submit="applyFilters">
          <input
            v-model="keyword"
            type="search"
            placeholder="搜索问题、会话ID、用户ID、模型或错误"
          />
          <AdminSelect
            v-model="outcomeFilter"
            :options="outcomeFilterOptions"
            aria-label="识别结果"
            @change="applyFilters"
          />
          <AdminSelect
            v-model="ambiguousFilter"
            :options="ambiguousFilterOptions"
            aria-label="歧义状态"
            @change="applyFilters"
          />
          <AdminDateTimeRangeInput
            v-model:start-value="startAt"
            v-model:end-value="endAt"
            aria-label="识别时间范围"
            @change="applyFilters"
          />
          <button type="button" class="kc-ghost-button" :disabled="isLoading" @click="resetFilters">重置</button>
          <button type="submit" class="kc-primary-button" :disabled="isLoading">
            {{ isLoading ? '查询中...' : '查询' }}
          </button>
        </AdminFilterBar>
      </template>

      <template #row="{ row: record }">
          <span class="intent-record-query">
            <strong>{{ queryPreview(record) }}</strong>
            <small>#{{ record.id }} · C{{ record.conversationId }} · U{{ record.userId }}</small>
          </span>
          <span>
            <StatusBadge
              :label="outcomeText(record.outcome)"
              :variant="outcomeClass(record)"
              :note="record.ambiguous ? 'ambiguous' : ''"
            />
          </span>
          <span class="intent-record-node-list">
            <small v-for="node in arrayValue(record.selectedNodes).slice(0, 2)" :key="`${record.id}-${selectedNodeLabel(node)}`">
              {{ selectedNodeLabel(node) }} · {{ scoreText(node.score) }}
            </small>
            <small v-if="arrayValue(record.selectedNodes).length === 0">-</small>
          </span>
          <span>{{ record.modelId || '-' }}</span>
          <span>{{ formatDuration(record.durationMs) }}</span>
          <span>{{ formatDate(record.createdAt) }}</span>
          <span class="kc-row-actions compact">
            <button type="button" @click="selectedRecord = record">详情</button>
          </span>
      </template>
    </AdminTable>

    <AdminDetailModal
      :open="Boolean(selectedRecord)"
      title="意图识别详情"
      :subtitle="selectedRecordSubtitle"
      @close="selectedRecord = null"
    >
      <template v-if="selectedRecord">
        <div class="kc-detail-panel grouped">
          <section class="kc-detail-section">
            <h3>基础信息</h3>
            <div>
              <span>
                结果:
                <StatusBadge
                  :label="outcomeText(selectedRecord.outcome)"
                  :variant="outcomeClass(selectedRecord)"
                />
              </span>
              <span>歧义: {{ selectedRecord.ambiguous ? '是' : '否' }}</span>
              <span>会话ID: {{ selectedRecord.conversationId }}</span>
              <span>用户ID: {{ selectedRecord.userId }}</span>
              <span>消息ID: {{ selectedRecord.userMessageId || '-' }}</span>
              <span>耗时: {{ formatDuration(selectedRecord.durationMs) }}</span>
              <span>模型: {{ selectedRecord.modelId || '-' }}</span>
              <span>降级原因: {{ selectedRecord.fallbackReason || '-' }}</span>
            </div>
          </section>

          <section class="kc-detail-section">
            <h3>问题链路</h3>
            <div class="kc-detail-flow">
              <span><b>原始问题</b>{{ selectedRecord.originalQuery || '-' }}</span>
              <span><b>术语统一</b>{{ selectedRecord.normalizedQuery || '-' }}</span>
              <span><b>语义改写</b>{{ selectedRecord.rewrittenQuery || '-' }}</span>
              <span><b>澄清问题</b>{{ selectedRecord.guidanceQuestion || '-' }}</span>
            </div>
          </section>

          <section class="kc-detail-section">
            <h3>命中节点</h3>
            <div class="kc-detail-list">
              <article v-for="node in arrayValue(selectedRecord.selectedNodes)" :key="`${selectedRecord.id}-${selectedNodeLabel(node)}-${node.source}`">
                <strong>{{ selectedNodeLabel(node) }}</strong>
                <small>{{ node.kind || '-' }} · {{ node.source || '-' }} · score {{ scoreText(node.score) }}</small>
                <p>{{ node.reason || '-' }}</p>
              </article>
              <p v-if="arrayValue(selectedRecord.selectedNodes).length === 0" class="kc-empty">没有命中节点。</p>
            </div>
          </section>

          <section class="kc-detail-section">
            <h3>Bad case 回放</h3>
            <div class="bad-case-replay-panel">
              <label class="bad-case-field">
                <span>期望强命中节点</span>
                <input
                  v-model.trim="badCaseExpectedNodeCode"
                  type="text"
                  placeholder="NONE 或节点编码"
                  @input="badCaseCopyState = 'idle'"
                />
              </label>
              <div class="bad-case-option-list">
                <button
                  v-for="option in badCaseExpectedOptions"
                  :key="option"
                  type="button"
                  :class="{ active: normalizeExpectedNodeCode(badCaseExpectedNodeCode) === option }"
                  @click="setBadCaseExpectedNodeCode(option)"
                >
                  {{ option }}
                </button>
              </div>
              <pre class="bad-case-preview">{{ badCaseCsvLine }}</pre>
              <div class="bad-case-actions">
                <button type="button" class="kc-primary-button" @click="copyBadCaseSample">
                  {{ badCaseCopyState === 'copied' ? '已复制' : '复制为测试样例' }}
                </button>
                <span :class="{ danger: badCaseCopyState === 'failed' }">
                  {{ badCaseCopyState === 'failed' ? '复制失败' : 'intent-rule-bad-cases.csv' }}
                </span>
              </div>
            </div>
          </section>

          <section class="kc-detail-section">
            <h3>完整 JSON</h3>
            <AdminJsonPreview :value="{
              subQuestions: selectedRecord.subQuestions,
              intents: selectedRecord.intents,
              selectedNodes: selectedRecord.selectedNodes,
              subQuestionIntents: selectedRecord.subQuestionIntents,
              errorMessage: selectedRecord.errorMessage
            }" />
          </section>
        </div>
      </template>
      <template #footer>
        <button type="button" class="kc-ghost-button" @click="selectedRecord = null">关闭</button>
      </template>
    </AdminDetailModal>
  </section>
</template>
