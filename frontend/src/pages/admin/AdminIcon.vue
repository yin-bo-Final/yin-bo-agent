<script setup>
defineProps({
  name: {
    type: String,
    required: true
  }
});
</script>

<template>
  <svg v-if="name === 'dashboard'" viewBox="0 0 24 24">
    <path d="M4 13h6V4H4v9Z" />
    <path d="M14 20h6V4h-6v16Z" />
    <path d="M4 20h6v-3H4v3Z" />
  </svg>
  <svg v-else-if="name === 'knowledge'" viewBox="0 0 24 24">
    <path d="M5 5.5A2.5 2.5 0 0 1 7.5 3H20v16H7.5A2.5 2.5 0 0 0 5 21.5v-16Z" />
    <path d="M5 5.5A2.5 2.5 0 0 0 2.5 3H2v16h.5A2.5 2.5 0 0 1 5 21.5" />
    <path d="M9 8h7" />
    <path d="M9 12h6" />
  </svg>
  <svg v-else-if="name === 'documents'" viewBox="0 0 24 24">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
    <path d="M14 2v6h6" />
    <path d="M8 13h8" />
    <path d="M8 17h6" />
  </svg>
  <svg v-else-if="name === 'chunks'" viewBox="0 0 24 24">
    <path d="M4 7h16" />
    <path d="M4 12h10" />
    <path d="M4 17h16" />
    <path d="M17 10l3 2-3 2" />
  </svg>
  <svg v-else-if="name === 'tasks'" viewBox="0 0 24 24">
    <path d="M4 5h16" />
    <path d="M4 12h10" />
    <path d="M4 19h7" />
    <path d="M17 15l4 4" />
    <path d="M21 15l-4 4" />
  </svg>
  <svg v-else-if="name === 'mappings'" viewBox="0 0 24 24">
    <path d="M4 7h7" />
    <path d="M4 17h7" />
    <path d="M15 7h5" />
    <path d="M15 17h5" />
    <path d="M11 7l4 10" />
  </svg>
  <svg v-else-if="name === 'pipeline'" viewBox="0 0 24 24">
    <path d="M4 6h5" />
    <path d="M15 6h5" />
    <path d="M9 6h6" />
    <path d="M4 18h5" />
    <path d="M15 18h5" />
    <path d="M12 9v6" />
  </svg>
  <svg v-else-if="name === 'queryRecords'" viewBox="0 0 24 24">
    <path d="M5 6h14" />
    <path d="M5 12h7" />
    <path d="M5 18h5" />
    <path d="M15 11l4 4" />
    <path d="M19 11l-4 4" />
  </svg>
  <svg v-else-if="name === 'intentTree'" viewBox="0 0 24 24">
    <path d="M12 5v4" />
    <path d="M6.5 13h11" />
    <path d="M6.5 13v4" />
    <path d="M12 9v8" />
    <path d="M17.5 13v4" />
    <rect x="9.5" y="3" width="5" height="4" rx="1.4" />
    <rect x="4" y="17" width="5" height="4" rx="1.4" />
    <rect x="9.5" y="17" width="5" height="4" rx="1.4" />
    <rect x="15" y="17" width="5" height="4" rx="1.4" />
  </svg>
  <svg v-else-if="name === 'intentList'" viewBox="0 0 24 24">
    <rect x="4" y="4" width="16" height="16" rx="3" />
    <path d="M8 8h.01" />
    <path d="M11 8h5" />
    <path d="M8 12h.01" />
    <path d="M11 12h5" />
    <path d="M8 16h.01" />
    <path d="M11 16h5" />
  </svg>
  <svg v-else-if="name === 'intentRules'" viewBox="0 0 24 24">
    <path d="M4 7h10" />
    <path d="M18 7h2" />
    <path d="M4 12h3" />
    <path d="M11 12h9" />
    <path d="M4 17h8" />
    <path d="M16 17h4" />
    <circle cx="16" cy="7" r="2" />
    <circle cx="9" cy="12" r="2" />
    <circle cx="14" cy="17" r="2" />
  </svg>
  <svg v-else-if="name === 'intentRecords'" viewBox="0 0 24 24">
    <path d="M5 5h14" />
    <path d="M5 12h8" />
    <path d="M5 19h6" />
    <path d="M16 14l3 3" />
    <circle cx="15" cy="13" r="3" />
  </svg>
  <svg v-else-if="name === 'sidebar'" viewBox="0 0 24 24">
    <rect x="4" y="5" width="16" height="14" rx="3" />
    <path d="M9 5v14" />
  </svg>
  <svg v-else-if="name === 'back'" viewBox="0 0 24 24">
    <path d="M15 18l-6-6 6-6" />
    <path d="M9 12h12" />
  </svg>
  <svg v-else-if="name === 'brand'" viewBox="0 0 24 24">
    <path d="M4 5h16" />
    <path d="M4 12h16" />
    <path d="M4 19h16" />
    <path d="M8 5v14" />
    <path d="M16 5v14" />
  </svg>
  <svg v-else-if="name === 'refresh'" viewBox="0 0 24 24">
    <path d="M20 6v5h-5" />
    <path d="M4 18v-5h5" />
    <path d="M19 11a7 7 0 0 0-12.2-4.2L4 9" />
    <path d="M5 13a7 7 0 0 0 12.2 4.2L20 15" />
  </svg>
  <svg v-else viewBox="0 0 24 24">
    <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
    <path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15Z" />
    <path d="M8 7h8" />
    <path d="M8 11h6" />
  </svg>
</template>
