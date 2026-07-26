<script setup>
import { computed } from 'vue';

const props = defineProps({
  monthCursor: {
    type: Date,
    required: true
  },
  startDate: {
    type: String,
    default: ''
  },
  endDate: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:monthCursor', 'select-date']);

const weekLabels = ['一', '二', '三', '四', '五', '六', '日'];

const monthTitle = computed(() => `${props.monthCursor.getFullYear()}年${pad(props.monthCursor.getMonth() + 1)}月`);
const calendarDays = computed(() => {
  const firstDay = new Date(props.monthCursor.getFullYear(), props.monthCursor.getMonth(), 1);
  const mondayOffset = (firstDay.getDay() + 6) % 7;
  const gridStart = new Date(firstDay);
  gridStart.setDate(firstDay.getDate() - mondayOffset);
  const rangeStart = props.startDate && props.endDate ? [props.startDate, props.endDate].sort()[0] : '';
  const rangeEnd = props.startDate && props.endDate ? [props.startDate, props.endDate].sort()[1] : '';
  const today = toDateValue(new Date());

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    const value = toDateValue(date);
    return {
      value,
      day: date.getDate(),
      inMonth: date.getMonth() === props.monthCursor.getMonth(),
      isToday: value === today,
      isSelected: value === props.startDate || value === props.endDate,
      isRangeEdge: value === props.startDate || value === props.endDate,
      isInRange: Boolean(rangeStart && rangeEnd && value >= rangeStart && value <= rangeEnd)
    };
  });
});

function moveMonth(offset) {
  emit('update:monthCursor', monthStart(new Date(
    props.monthCursor.getFullYear(),
    props.monthCursor.getMonth() + offset,
    1
  )));
}

function moveYear(offset) {
  emit('update:monthCursor', monthStart(new Date(
    props.monthCursor.getFullYear() + offset,
    props.monthCursor.getMonth(),
    1
  )));
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

function pad(value) {
  return String(value).padStart(2, '0');
}
</script>

<template>
  <div class="kc-date-picker-calendar">
    <div class="kc-date-picker-toolbar">
      <button type="button" aria-label="上一年" @click="moveYear(-1)">«</button>
      <button type="button" aria-label="上个月" @click="moveMonth(-1)">‹</button>
      <strong>{{ monthTitle }}</strong>
      <button type="button" aria-label="下个月" @click="moveMonth(1)">›</button>
      <button type="button" aria-label="下一年" @click="moveYear(1)">»</button>
    </div>
    <div class="kc-date-picker-weekdays">
      <span v-for="label in weekLabels" :key="label">{{ label }}</span>
    </div>
    <div class="kc-date-picker-days">
      <button
        v-for="day in calendarDays"
        :key="day.value"
        type="button"
        :class="{
          muted: !day.inMonth,
          today: day.isToday,
          selected: day.isSelected,
          'range-edge': day.isRangeEdge,
          'in-range': day.isInRange
        }"
        @click="emit('select-date', day.value)"
      >
        {{ day.day }}
      </button>
    </div>
  </div>
</template>
