<script setup>
import { onMounted, onUnmounted, ref } from 'vue';

const TOAST_DURATION_MS = 5200;
const toasts = ref([]);
const timers = new Map();
let nextToastId = 1;

onMounted(() => {
  window.addEventListener('yinbo-api-error', handleApiError);
});

onUnmounted(() => {
  window.removeEventListener('yinbo-api-error', handleApiError);
  timers.forEach((timerId) => window.clearTimeout(timerId));
  timers.clear();
});

function handleApiError(event) {
  const detail = event.detail || {};
  if (detail.status !== 429) {
    return;
  }

  const toast = {
    id: nextToastId,
    message: detail.message || '请求过于频繁，请稍后再试',
    requestId: detail.requestId,
    duration: TOAST_DURATION_MS
  };
  nextToastId += 1;
  toasts.value = [...toasts.value, toast];
  timers.set(toast.id, window.setTimeout(() => dismissToast(toast.id), TOAST_DURATION_MS));
}

function dismissToast(toastId) {
  const timerId = timers.get(toastId);
  if (timerId) {
    window.clearTimeout(timerId);
    timers.delete(toastId);
  }
  toasts.value = toasts.value.filter((toast) => toast.id !== toastId);
}
</script>

<template>
  <Teleport to="body">
    <TransitionGroup
      v-if="toasts.length"
      tag="section"
      name="global-toast"
      class="global-toast-stack"
      aria-live="assertive"
      aria-label="全局错误提示"
    >
      <article
        v-for="toast in toasts"
        :key="toast.id"
        class="global-error-toast"
        role="alert"
      >
        <p>{{ toast.message }}</p>
        <button
          type="button"
          class="global-error-toast-close"
          :style="{ '--toast-duration': `${toast.duration}ms` }"
          aria-label="关闭提示"
          @click="dismissToast(toast.id)"
        >
          <svg class="global-error-toast-progress" viewBox="0 0 36 36" aria-hidden="true">
            <circle class="global-error-toast-track" cx="18" cy="18" r="14" />
            <circle class="global-error-toast-bar" cx="18" cy="18" r="14" />
          </svg>
          <span>×</span>
        </button>
      </article>
    </TransitionGroup>
  </Teleport>
</template>
