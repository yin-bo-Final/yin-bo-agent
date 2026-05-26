<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { fetchCurrentUser } from './api/authApi';
import AdminPage from './pages/AdminPage.vue';
import AuthPage from './pages/AuthPage.vue';
import ConversationPage from './pages/ConversationPage.vue';

const currentUser = ref(null);
const isCheckingSession = ref(true);
const activePage = ref(window.location.pathname.startsWith('/admin') ? 'admin' : 'chat');
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN');

onMounted(async () => {
  window.addEventListener('popstate', syncRouteWithLocation);
  try {
    const response = await fetchCurrentUser();
    currentUser.value = response.user;
    syncRouteWithLocation();
  } catch (_error) {
    currentUser.value = null;
    replaceHomeUrl();
  } finally {
    isCheckingSession.value = false;
  }
});

onUnmounted(() => {
  window.removeEventListener('popstate', syncRouteWithLocation);
});

function handleAuthenticated(user) {
  currentUser.value = user;
  syncRouteWithLocation();
}

function handleUnauthenticated() {
  currentUser.value = null;
  replaceHomeUrl();
}

function replaceHomeUrl() {
  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  if (currentPath !== '/') {
    window.history.replaceState({}, '', '/');
  }
  activePage.value = 'chat';
}

function syncRouteWithLocation() {
  const wantsAdmin = window.location.pathname.startsWith('/admin');
  if (wantsAdmin && isAdmin.value) {
    activePage.value = 'admin';
    return;
  }
  if (wantsAdmin && currentUser.value && !isAdmin.value) {
    window.history.replaceState({}, '', '/');
  }
  activePage.value = 'chat';
}

function openAdminPage() {
  if (!isAdmin.value) {
    return;
  }
  window.history.pushState({}, '', '/admin');
  activePage.value = 'admin';
}

function openChatPage() {
  window.history.pushState({}, '', '/');
  activePage.value = 'chat';
}
</script>

<template>
  <AdminPage
    v-if="currentUser && activePage === 'admin' && isAdmin"
    :current-user="currentUser"
    @back-to-chat="openChatPage"
    @logged-out="handleUnauthenticated"
    @session-expired="handleUnauthenticated"
  />
  <ConversationPage
    v-else-if="currentUser"
    :current-user="currentUser"
    @logged-out="handleUnauthenticated"
    @account-cancelled="handleUnauthenticated"
    @session-expired="handleUnauthenticated"
    @open-admin="openAdminPage"
  />
  <AuthPage
    v-else
    :is-checking-session="isCheckingSession"
    @authenticated="handleAuthenticated"
  />
</template>
