<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';

const props = defineProps({
  modelValue: {
    type: [String, Number, Boolean],
    default: ''
  },
  options: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: '请选择'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  ariaLabel: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:modelValue', 'change']);

const root = ref(null);
const isOpen = ref(false);

const normalizedOptions = computed(() => {
  return props.options.map((option) => {
    if (typeof option === 'object' && option !== null) {
      return {
        label: option.label ?? String(option.value ?? ''),
        value: option.value ?? option.label ?? '',
        disabled: Boolean(option.disabled)
      };
    }
    return {
      label: String(option),
      value: option,
      disabled: false
    };
  });
});

const selectedOption = computed(() => {
  return normalizedOptions.value.find((option) => Object.is(option.value, props.modelValue));
});

const selectedLabel = computed(() => {
  return selectedOption.value?.label || props.placeholder;
});

function toggleOpen() {
  if (props.disabled) {
    return;
  }
  isOpen.value = !isOpen.value;
}

function close() {
  isOpen.value = false;
}

function selectOption(option) {
  if (option.disabled) {
    return;
  }
  emit('update:modelValue', option.value);
  emit('change', option.value);
  close();
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
  <div ref="root" class="kc-select-menu" :class="{ open: isOpen, disabled }">
    <button
      type="button"
      class="kc-select-trigger"
      :disabled="disabled"
      :aria-label="ariaLabel || selectedLabel"
      :aria-expanded="isOpen"
      @click="toggleOpen"
    >
      <span>{{ selectedLabel }}</span>
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9l6 6 6-6" /></svg>
    </button>
    <div v-if="isOpen" class="kc-select-options">
      <button
        v-for="option in normalizedOptions"
        :key="String(option.value)"
        type="button"
        :disabled="option.disabled"
        :class="{ active: Object.is(option.value, modelValue) }"
        @click="selectOption(option)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>
