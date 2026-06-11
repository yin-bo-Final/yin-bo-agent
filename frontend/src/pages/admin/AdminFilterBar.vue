<script setup>
defineProps({
  wide: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['submit']);
</script>

<template>
  <form
    class="kc-toolbar-actions kc-filter-bar"
    :class="{ wide }"
    @submit.prevent="emit('submit')"
  >
    <slot />
  </form>
</template>
