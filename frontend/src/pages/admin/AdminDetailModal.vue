<script setup>
defineProps({
  open: {
    type: Boolean,
    required: true
  },
  title: {
    type: String,
    required: true
  },
  subtitle: {
    type: String,
    default: ''
  },
  wide: {
    type: Boolean,
    default: true
  },
  modalClass: {
    type: [String, Array, Object],
    default: ''
  }
});

const emit = defineEmits(['close']);
</script>

<template>
  <div v-if="open" class="kc-modal-backdrop" @click.self="emit('close')">
    <section class="kc-modal kc-detail-modal" :class="[{ wide }, modalClass]">
      <header>
        <div>
          <h2>{{ title }}</h2>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
        <button type="button" class="kc-icon-button" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <slot />

      <footer v-if="$slots.footer">
        <slot name="footer" />
      </footer>
    </section>
  </div>
</template>
