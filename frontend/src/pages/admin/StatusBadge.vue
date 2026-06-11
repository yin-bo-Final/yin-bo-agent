<script setup>
defineProps({
  label: {
    type: String,
    default: ''
  },
  variant: {
    type: String,
    default: 'muted'
  },
  note: {
    type: String,
    default: ''
  }
});
</script>

<template>
  <span class="admin-status-badge">
    <span class="kc-status" :class="variant">
      <slot>{{ label }}</slot>
    </span>
    <small v-if="note" class="kc-status-note">{{ note }}</small>
  </span>
</template>
