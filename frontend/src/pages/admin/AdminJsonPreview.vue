<script setup>
import { computed } from 'vue';

const props = defineProps({
  value: {
    type: null,
    default: null
  }
});

const jsonText = computed(() => JSON.stringify(props.value ?? [], null, 2));
</script>

<template>
  <pre class="kc-json-preview">{{ jsonText }}</pre>
</template>
