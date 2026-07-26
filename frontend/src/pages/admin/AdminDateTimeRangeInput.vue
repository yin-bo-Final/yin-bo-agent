<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import AdminDateRangeCalendar from './AdminDateRangeCalendar.vue';

const props = defineProps({
  startValue: {
    type: String,
    default: ''
  },
  endValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '时间范围'
  },
  ariaLabel: {
    type: String,
    default: '选择时间范围'
  },
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:startValue', 'update:endValue', 'change']);

const root = ref(null);
const isOpen = ref(false);
const monthCursor = ref(monthStart(new Date()));
const draftStartDate = ref('');
const draftEndDate = ref('');
const isPickingEnd = ref(false);

const hasRange = computed(() => Boolean(props.startValue || props.endValue));
const triggerLabel = computed(() => {
  if (props.startValue && props.endValue) {
    return `${formatDate(props.startValue)} - ${formatDate(props.endValue)}`;
  }
  if (props.startValue) {
    return `开始 ${formatDate(props.startValue)}`;
  }
  if (props.endValue) {
    return `结束 ${formatDate(props.endValue)}`;
  }
  return props.placeholder;
});
const canConfirm = computed(() => Boolean(draftStartDate.value && draftEndDate.value));

function toggleOpen() {
  if (props.disabled) {
    return;
  }
  if (isOpen.value) {
    close();
    return;
  }
  draftStartDate.value = datePart(props.startValue);
  draftEndDate.value = datePart(props.endValue);
  isPickingEnd.value = Boolean(draftStartDate.value && !draftEndDate.value);
  syncMonthToCurrentRange();
  isOpen.value = true;
}

function close() {
  isOpen.value = false;
}

function chooseDate(dateValue) {
  if (!isPickingEnd.value || !draftStartDate.value) {
    draftStartDate.value = dateValue;
    draftEndDate.value = '';
    isPickingEnd.value = true;
    return;
  }

  draftEndDate.value = dateValue;
}

function confirmRange() {
  if (!canConfirm.value) {
    return;
  }
  applyDateRange(draftStartDate.value, draftEndDate.value, 'confirm');
}

function applyTodayRange() {
  const end = new Date();
  setDraftRange(toDateValue(end), toDateValue(end));
}

function applyWeekRange() {
  const end = new Date();
  const start = new Date(end);
  start.setDate(end.getDate() - ((end.getDay() + 6) % 7));
  setDraftRange(toDateValue(start), toDateValue(end));
}

function applyMonthRange() {
  const end = new Date();
  const start = new Date(end.getFullYear(), end.getMonth(), 1);
  setDraftRange(toDateValue(start), toDateValue(end));
}

function setDraftRange(startDate, endDate) {
  const normalized = normalizeDateRange(startDate, endDate);
  draftStartDate.value = normalized.startDate;
  draftEndDate.value = normalized.endDate;
  isPickingEnd.value = false;
  const parts = parseDateValue(normalized.endDate || normalized.startDate);
  if (parts) {
    monthCursor.value = monthStart(new Date(parts.year, parts.month - 1, 1));
  }
}

function clearRange() {
  emit('update:startValue', '');
  emit('update:endValue', '');
  emit('change', { startValue: '', endValue: '', source: 'clear' });
  close();
}

function applyDateRange(startDate, endDate, source) {
  const normalized = normalizeDateRange(startDate, endDate);
  const startValue = startOfDay(normalized.startDate);
  const endValue = endOfDay(normalized.endDate);
  emit('update:startValue', startValue);
  emit('update:endValue', endValue);
  emit('change', {
    startValue,
    endValue,
    source
  });
  close();
}

function syncMonthToCurrentRange() {
  const visibleDate = draftStartDate.value || draftEndDate.value || datePart(props.startValue) || datePart(props.endValue);
  const parts = parseDateValue(visibleDate);
  if (parts) {
    monthCursor.value = monthStart(new Date(parts.year, parts.month - 1, 1));
  }
}

function normalizeDateRange(startDate, endDate) {
  if (startDate && endDate && startDate > endDate) {
    return {
      startDate: endDate,
      endDate: startDate
    };
  }
  return {
    startDate: startDate || '',
    endDate: endDate || ''
  };
}

function formatDate(value) {
  const parts = parseDateTimeValue(value) || parseDateValue(value);
  if (!parts) {
    return '';
  }
  return `${parts.year}/${parts.month}/${parts.day}`;
}

function parseDateTimeValue(value) {
  const [datePart, timePart = ''] = String(value || '').split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  const [hour = '', minute = ''] = timePart.split(':');
  if (!year || !month || !day) {
    return null;
  }
  return {
    year,
    month,
    day,
    hour: hour || '00',
    minute: minute || '00',
    dateValue: `${year}-${pad(month)}-${pad(day)}`
  };
}

function parseDateValue(value) {
  const [year, month, day] = String(value || '').split('-').map(Number);
  if (!year || !month || !day) {
    return null;
  }
  return {
    year,
    month,
    day,
    dateValue: `${year}-${pad(month)}-${pad(day)}`
  };
}

function datePart(value) {
  return parseDateTimeValue(value)?.dateValue || '';
}

function monthStart(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function toDateValue(date) {
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate())
  ].join('-');
}

function startOfDay(dateValue) {
  return `${dateValue}T00:00`;
}

function endOfDay(dateValue) {
  return `${dateValue}T23:59`;
}

function pad(value) {
  return String(value).padStart(2, '0');
}

function handleOutsidePointerDown(event) {
  if (!root.value || root.value.contains(event.target)) {
    return;
  }
  close();
}

function handleEscape(event) {
  if (event.key === 'Escape') {
    close();
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handleOutsidePointerDown);
  document.addEventListener('keydown', handleEscape);
});

onUnmounted(() => {
  document.removeEventListener('pointerdown', handleOutsidePointerDown);
  document.removeEventListener('keydown', handleEscape);
});
</script>

<template>
  <div ref="root" class="kc-date-range-menu" :class="{ open: isOpen, filled: hasRange, disabled }">
    <button
      type="button"
      class="kc-date-range-trigger"
      :disabled="disabled"
      :aria-label="ariaLabel"
      :aria-expanded="isOpen"
      @click="toggleOpen"
    >
      <span>{{ triggerLabel }}</span>
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <rect x="5" y="5" width="14" height="14" rx="2" />
        <path d="M8 3v4" />
        <path d="M16 3v4" />
        <path d="M5 10h14" />
      </svg>
    </button>

    <section v-if="isOpen" class="kc-date-range-panel">
      <header>
        <strong>选择时间范围</strong>
      </header>

      <div class="kc-date-picker-surface">
        <AdminDateRangeCalendar
          v-model:month-cursor="monthCursor"
          :start-date="draftStartDate"
          :end-date="draftEndDate"
          @select-date="chooseDate"
        />
      </div>

      <div class="kc-date-range-quick">
        <button type="button" @click="applyTodayRange">本日</button>
        <button type="button" @click="applyWeekRange">本周</button>
        <button type="button" @click="applyMonthRange">本月</button>
        <button type="button" @click="clearRange">全部</button>
      </div>

      <footer>
        <button type="button" class="kc-primary-button" :disabled="!canConfirm" @click="confirmRange">确定</button>
      </footer>
    </section>
  </div>
</template>
