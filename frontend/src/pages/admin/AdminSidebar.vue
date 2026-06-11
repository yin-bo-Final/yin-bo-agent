<script setup>
import AdminIcon from './AdminIcon.vue';

defineProps({
  activeModule: {
    type: String,
    required: true
  },
  collapsed: {
    type: Boolean,
    required: true
  },
  currentUser: {
    type: Object,
    required: true
  }
});

const emit = defineEmits(['back-to-chat', 'navigate', 'toggle']);

const navItems = [
  { module: 'dashboard', label: 'DashBoard', tooltip: 'DashBoard', icon: 'dashboard', path: '/admin' },
  { module: 'knowledge', label: '知识库管理', tooltip: '知识库管理', icon: 'knowledge', path: '/admin/knowledge' },
  { module: 'tasks', label: '失败任务', tooltip: '失败任务', icon: 'tasks', path: '/admin/tasks/failed' },
  { module: 'mappings', label: '关键词映射', tooltip: '关键词映射', icon: 'mappings', path: '/admin/mappings' },
  { module: 'pipeline', label: '流水线配置', tooltip: '流水线配置', icon: 'pipeline', path: '/admin/pipeline' },
  { module: 'query-records', label: '改写记录', tooltip: '改写记录', icon: 'queryRecords', path: '/admin/query-records' }
];

const intentNavItems = [
  { module: 'intent-tree', label: '意图树配置', tooltip: '意图树配置', icon: 'intentTree', path: '/admin/intent-tree' },
  { module: 'intent-list', label: '意图列表', tooltip: '意图列表', icon: 'intentList', path: '/admin/intent-list' },
  { module: 'intent-rules', label: '规则配置', tooltip: '规则配置', icon: 'intentRules', path: '/admin/intent-rules' },
  { module: 'intent-records', label: '识别记录', tooltip: '识别记录', icon: 'intentRecords', path: '/admin/intent-records' }
];

function navigate(path) {
  emit('navigate', path);
}
</script>

<template>
  <nav v-if="collapsed" class="admin-sidebar-rail" aria-label="后台管理快捷导航">
    <button class="admin-rail-button" type="button" data-tooltip="展开导航栏" @click="emit('toggle')">
      <AdminIcon name="sidebar" aria-hidden="true" />
    </button>
    <button
      v-for="item in [...navItems, ...intentNavItems]"
      :key="item.module"
      class="admin-rail-button"
      type="button"
      :class="{ active: activeModule === item.module }"
      :data-tooltip="item.tooltip"
      @click="navigate(item.path)"
    >
      <AdminIcon :name="item.icon" aria-hidden="true" />
    </button>
    <button class="admin-rail-button admin-rail-bottom" type="button" data-tooltip="返回会话" @click="emit('back-to-chat')">
      <AdminIcon name="back" aria-hidden="true" />
    </button>
  </nav>

  <aside class="admin-sidebar">
    <div class="admin-brand">
      <span class="admin-brand-mark">
        <AdminIcon name="brand" aria-hidden="true" />
      </span>
      <div>
        <strong>后台管理</strong>
        <small>{{ currentUser.displayName || currentUser.username }}</small>
      </div>
      <button class="admin-sidebar-toggle" type="button" @click="emit('toggle')">
        <AdminIcon name="sidebar" aria-hidden="true" />
      </button>
    </div>

    <nav class="admin-nav" aria-label="后台管理导航">
      <button
        v-for="item in navItems"
        :key="item.module"
        type="button"
        :class="{ active: activeModule === item.module }"
        @click="navigate(item.path)"
      >
        <AdminIcon :name="item.icon" aria-hidden="true" />
        <span>{{ item.label }}</span>
      </button>
      <div class="admin-nav-group">
        <small>意图管理</small>
        <button
          v-for="item in intentNavItems"
          :key="item.module"
          type="button"
          :class="{ active: activeModule === item.module }"
          @click="navigate(item.path)"
        >
          <AdminIcon :name="item.icon" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </button>
      </div>
    </nav>

    <button class="admin-back-button" type="button" @click="emit('back-to-chat')">
      <AdminIcon name="back" aria-hidden="true" />
      <span>返回会话</span>
    </button>
  </aside>
</template>
