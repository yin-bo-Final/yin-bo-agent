<script setup>
import AdminIcon from './AdminIcon.vue';

defineProps({
  currentHeader: {
    type: Object,
    required: true
  },
  isRefreshing: {
    type: Boolean,
    required: true
  }
});

const emit = defineEmits(['refresh']);
</script>

<template>
  <header class="admin-header">
    <div>
      <span>{{ currentHeader.label }}</span>
      <h1>
        <i class="admin-title-icon" :class="`admin-title-icon-${currentHeader.icon}`" aria-hidden="true">
          <AdminIcon :name="currentHeader.icon" />
        </i>
        {{ currentHeader.title }}
      </h1>
    </div>
    <button
      type="button"
      class="admin-refresh-button"
      :class="{ refreshing: isRefreshing }"
      :disabled="isRefreshing"
      @click="emit('refresh')"
    >
      <AdminIcon name="refresh" aria-hidden="true" />
      <span>刷新</span>
    </button>
  </header>
</template>
