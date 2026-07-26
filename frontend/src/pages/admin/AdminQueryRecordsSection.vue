<script setup>
import { computed, onMounted, ref } from 'vue';
import { fetchQueryRewriteRecords } from '../../api/adminApi';
import AdminDateTimeRangeInput from './AdminDateTimeRangeInput.vue';
import AdminDetailModal from './AdminDetailModal.vue';
import AdminFilterBar from './AdminFilterBar.vue';
import AdminJsonPreview from './AdminJsonPreview.vue';
import AdminSelect from './AdminSelect.vue';
import AdminTable from './AdminTable.vue';
import { formatDate, formatDuration, metricText } from './adminPageUtils';
import StatusBadge from './StatusBadge.vue';

const emit = defineEmits(['error']);

const sourceTypeFilterOptions = [
  { label: '全部来源', value: 'ALL' },
  { label: 'llm', value: 'LLM' },
  { label: 'rule_split', value: 'RULE_SPLIT' },
  { label: 'fallback', value: 'FALLBACK' }
];
const successFilterOptions = [
  { label: '全部状态', value: 'ALL' },
  { label: 'success', value: 'true' },
  { label: 'failed', value: 'false' }
];

const records = ref([]);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const pages = ref(0);
const keyword = ref('');
const sourceTypeFilter = ref('ALL');
const successFilter = ref('ALL');
const startAt = ref('');
const endAt = ref('');
const isLoading = ref(false);
const selectedRecord = ref(null);

const queryRecordColumns = [
  '问题',
  '来源',
  '子问题',
  '术语',
  '模型',
  '耗时',
  '时间',
  '操作'
];
const successCount = computed(() => records.value.filter((record) => record.success).length);
const fallbackCount = computed(() => records.value.filter((record) => record.sourceType === 'FALLBACK' || !record.success).length);
const llmCount = computed(() => records.value.filter((record) => record.sourceType === 'LLM').length);
const selectedRecordSubtitle = computed(() => {
  return selectedRecord.value ? `#${selectedRecord.value.id} · ${formatDate(selectedRecord.value.createdAt)}` : '';
});

onMounted(loadRecords);

defineExpose({
  reloadRecords: loadRecords
});

async function loadRecords() {
  if (isLoading.value) {
    return;
  }
  isLoading.value = true;
  try {
    const response = await fetchQueryRewriteRecords({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim(),
      sourceType: sourceTypeFilter.value,
      success: successFilter.value,
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
  sourceTypeFilter.value = 'ALL';
  successFilter.value = 'ALL';
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

function queryPreview(record) {
  return record.rewrittenQuery || record.normalizedQuery || record.originalQuery || '-';
}

function sourceTypeText(value) {
  return value ? String(value).toLowerCase() : 'unknown';
}

function sourceTypeClass(record) {
  if (record.sourceType === 'LLM') {
    return 'success';
  }
  if (record.sourceType === 'RULE_SPLIT') {
    return 'pending';
  }
  if (record.sourceType === 'FALLBACK' || record.success === false) {
    return 'danger';
  }
  return 'muted';
}

function arrayValue(value) {
  return Array.isArray(value) ? value : [];
}

function matchedTermLabel(term) {
  return term?.aliasName || term?.canonicalName || term?.term || '-';
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
            <path d="M4 7h7" />
            <path d="M15 7h5" />
            <path d="M11 7l4 10" />
          </svg>
        </span>
        <span>本页 LLM</span>
        <strong>{{ metricText(llmCount) }}</strong>
      </article>
    </div>

    <AdminTable
      title="查询改写记录"
      description="查看术语统一、语义改写、拆分结果、模型响应和耗时"
      card-class="intent-records-card"
      grid-class="query-record-grid"
      empty-text="没有匹配的查询改写记录。"
      :columns="queryRecordColumns"
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
            placeholder="搜索问题、会话ID、模型、版本或错误"
          />
          <AdminSelect
            v-model="sourceTypeFilter"
            :options="sourceTypeFilterOptions"
            aria-label="改写来源"
            @change="applyFilters"
          />
          <AdminSelect
            v-model="successFilter"
            :options="successFilterOptions"
            aria-label="改写状态"
            @change="applyFilters"
          />
          <AdminDateTimeRangeInput
            v-model:start-value="startAt"
            v-model:end-value="endAt"
            aria-label="改写时间范围"
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
              :label="sourceTypeText(record.sourceType)"
              :variant="sourceTypeClass(record)"
              :note="record.fallbackReason || ''"
            />
          </span>
          <span class="intent-record-node-list">
            <small v-for="item in arrayValue(record.subQuestions).slice(0, 2)" :key="`${record.id}-${item}`">{{ item }}</small>
            <small v-if="arrayValue(record.subQuestions).length === 0">-</small>
          </span>
          <span class="intent-record-node-list">
            <small v-for="term in arrayValue(record.matchedTerms).slice(0, 2)" :key="`${record.id}-${matchedTermLabel(term)}`">
              {{ matchedTermLabel(term) }}
            </small>
            <small v-if="arrayValue(record.matchedTerms).length === 0">-</small>
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
      title="查询改写详情"
      :subtitle="selectedRecordSubtitle"
      @close="selectedRecord = null"
    >
      <template v-if="selectedRecord">
        <div class="kc-detail-panel grouped">
          <section class="kc-detail-section">
            <h3>基础信息</h3>
            <div>
              <span>
                来源:
                <StatusBadge
                  :label="sourceTypeText(selectedRecord.sourceType)"
                  :variant="sourceTypeClass(selectedRecord)"
                />
              </span>
              <span>状态: {{ selectedRecord.success ? 'success' : 'failed' }}</span>
              <span>会话ID: {{ selectedRecord.conversationId }}</span>
              <span>用户ID: {{ selectedRecord.userId }}</span>
              <span>消息ID: {{ selectedRecord.userMessageId || '-' }}</span>
              <span>耗时: {{ formatDuration(selectedRecord.durationMs) }}</span>
              <span>模型: {{ selectedRecord.modelId || '-' }}</span>
              <span>Prompt: {{ selectedRecord.promptVersion || '-' }}</span>
              <span>降级原因: {{ selectedRecord.fallbackReason || '-' }}</span>
            </div>
          </section>

          <section class="kc-detail-section">
            <h3>问题链路</h3>
            <div class="kc-detail-flow">
              <span><b>原始问题</b>{{ selectedRecord.originalQuery || '-' }}</span>
              <span><b>术语统一</b>{{ selectedRecord.normalizedQuery || '-' }}</span>
              <span><b>语义改写</b>{{ selectedRecord.rewrittenQuery || '-' }}</span>
            </div>
          </section>

          <section class="kc-detail-section">
            <h3>完整 JSON</h3>
            <AdminJsonPreview :value="{
              subQuestions: selectedRecord.subQuestions,
              matchedTerms: selectedRecord.matchedTerms,
              errorMessage: selectedRecord.errorMessage,
              rawModelResponse: selectedRecord.rawModelResponse
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
