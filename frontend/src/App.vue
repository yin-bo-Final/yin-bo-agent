<script setup>
import { onMounted, ref } from 'vue';
import { fetchCurrentUser } from './api/authApi';
import AuthPage from './pages/AuthPage.vue';
import ConversationPage from './pages/ConversationPage.vue';

const currentUser = ref(null);
const isCheckingSession = ref(true);

onMounted(async () => {
  try {
    const response = await fetchCurrentUser();
    currentUser.value = response.user;
  } catch (_error) {
    currentUser.value = null;
    replaceHomeUrl();
  } finally {
    isCheckingSession.value = false;
  }
});

function handleAuthenticated(user) {
  currentUser.value = user;
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
}
</script>

<template>
  <ConversationPage
    v-if="currentUser"
    :current-user="currentUser"
    @logged-out="handleUnauthenticated"
    @account-cancelled="handleUnauthenticated"
    @session-expired="handleUnauthenticated"
  />
  <AuthPage
    v-else
    :is-checking-session="isCheckingSession"
    @authenticated="handleAuthenticated"
  />
</template>
