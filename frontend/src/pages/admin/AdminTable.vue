<script setup>
import { computed } from 'vue';
import { formatNumber } from './adminPageUtils';

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  columns: {
    type: Array,
    required: true
  },
  rows: {
    type: Array,
    default: () => []
  },
  rowKey: {
    type: [String, Function],
    default: 'id'
  },
  gridClass: {
    type: String,
    required: true
  },
  cardClass: {
    type: [String, Array, Object],
    default: ''
  },
  emptyText: {
    type: String,
    default: '暂无数据。'
  },
  loading: {
    type: Boolean,
    default: false
  },
  pagination: {
    type: Boolean,
    default: true
  },
  page: {
    type: Number,
    default: 1
  },
  pages: {
    type: Number,
    default: 1
  },
  pageSize: {
    type: Number,
    default: 20
  },
  pageSizes: {
    type: Array,
    default: () => [10, 20, 50, 100]
  },
  total: {
    type: Number,
    default: 0
  }
});

const emit = defineEmits(['page-change', 'page-size-change']);

const normalizedColumns = computed(() => props.columns.map((column) => {
  if (typeof column === 'string') {
    return {
      key: column,
      label: column
    };
  }
  return {
    key: column.key || column.label,
    label: column.label || column.key || ''
  };
}));

const pageCount = computed(() => Math.max(1, Number(props.pages || 1)));
const pageStart = computed(() => props.total === 0 ? 0 : (props.page - 1) * props.pageSize + 1);
const pageEnd = computed(() => Math.min(props.total, props.page * props.pageSize));

function resolveRowKey(row, index) {
  if (typeof props.rowKey === 'function') {
    return props.rowKey(row, index);
  }
  if (props.rowKey && row?.[props.rowKey] !== null && row?.[props.rowKey] !== undefined) {
    return row[props.rowKey];
  }
  return row?.id ?? index;
}

function changePageSize(event) {
  emit('page-size-change', Number(event.target.value));
}
</script>

<template>
  <section class="kc-table-card" :class="cardClass">
    <div class="kc-card-toolbar">
      <div>
        <strong>{{ title }}</strong>
        <small v-if="description">{{ description }}</small>
      </div>
      <slot name="actions" />
    </div>

    <div class="kc-table-head" :class="gridClass">
      <span v-for="column in normalizedColumns" :key="column.key">{{ column.label }}</span>
    </div>
    <div class="kc-table-body">
      <div
        v-for="(row, rowIndex) in rows"
        :key="resolveRowKey(row, rowIndex)"
        class="kc-table-row"
        :class="gridClass"
      >
        <slot name="row" :row="row" :index="rowIndex" />
      </div>
    </div>
    <p v-if="!loading && rows.length === 0" class="kc-empty">{{ emptyText }}</p>

    <footer v-if="pagination" class="kc-pagination">
      <span>第 {{ pageStart }}-{{ pageEnd }} 条 / 共 {{ formatNumber(total) }} 条</span>
      <div>
        <select :value="pageSize" :disabled="loading" @change="changePageSize">
          <option v-for="size in pageSizes" :key="size" :value="size">{{ size }} / 页</option>
        </select>
        <button
          type="button"
          class="kc-ghost-button"
          :disabled="loading || page <= 1"
          @click="emit('page-change', page - 1)"
        >
          上一页
        </button>
        <span>{{ page }} / {{ pageCount }}</span>
        <button
          type="button"
          class="kc-ghost-button"
          :disabled="loading || page >= pageCount"
          @click="emit('page-change', page + 1)"
        >
          下一页
        </button>
      </div>
    </footer>
  </section>
</template>
