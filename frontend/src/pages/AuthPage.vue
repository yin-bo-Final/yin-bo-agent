<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { login, register } from '../api/authApi';

const props = defineProps({
  isCheckingSession: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['authenticated']);

const isLoggingIn = ref(false);
const isRegistering = ref(false);
const loginError = ref('');
const authMode = ref('login');
const authForm = ref({
  username: '',
  password: ''
});
const isPasswordVisible = ref(false);
const authPointer = ref({
  x: 0,
  y: 0
});
let pendingAuthPointer = { x: 0, y: 0 };
let pointerAnimationFrame = 0;

const authPageStyle = computed(() => ({
  '--pointer-x': `${authPointer.value.x}px`,
  '--pointer-y': `${authPointer.value.y}px`
}));
const authSubmitText = computed(() => {
  if (props.isCheckingSession) {
    return '连接中...';
  }
  if (authMode.value === 'login') {
    return isLoggingIn.value ? '登录中...' : '登录';
  }
  return isRegistering.value ? '注册中...' : '注册';
});
const isAuthSubmitting = computed(() => {
  return props.isCheckingSession || (authMode.value === 'login' ? isLoggingIn.value : isRegistering.value);
});

onMounted(() => {
  const centerPoint = {
    x: window.innerWidth / 2,
    y: window.innerHeight / 2
  };
  pendingAuthPointer = centerPoint;
  authPointer.value = centerPoint;
});

onUnmounted(() => {
  stopPointerFrame();
});

async function submitLogin() {
  if (isLoggingIn.value || props.isCheckingSession) {
    return;
  }

  loginError.value = '';
  isLoggingIn.value = true;
  try {
    const response = await login(authForm.value);
    loginError.value = '';
    emit('authenticated', response.user);
  } catch (error) {
    loginError.value = error.message;
  } finally {
    isLoggingIn.value = false;
  }
}

async function submitRegister() {
  if (isRegistering.value || props.isCheckingSession) {
    return;
  }

  loginError.value = '';
  isRegistering.value = true;
  try {
    const response = await register(authForm.value);
    authMode.value = 'login';
    isPasswordVisible.value = false;
    emit('authenticated', response.user);
  } catch (error) {
    loginError.value = error.message;
  } finally {
    isRegistering.value = false;
  }
}

async function submitAuthForm() {
  if (authMode.value === 'login') {
    await submitLogin();
    return;
  }
  await submitRegister();
}

function togglePasswordVisibility() {
  isPasswordVisible.value = !isPasswordVisible.value;
}

function handleAuthPointerMove(event) {
  const rect = event.currentTarget.getBoundingClientRect();
  pendingAuthPointer = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  };
  schedulePointerFrame();
}

function resetAuthPointer(event) {
  const targetRect = event?.currentTarget?.getBoundingClientRect?.();
  pendingAuthPointer = {
    x: targetRect ? targetRect.width / 2 : window.innerWidth / 2,
    y: targetRect ? targetRect.height / 2 : window.innerHeight / 2
  };
  schedulePointerFrame();
}

function schedulePointerFrame() {
  if (pointerAnimationFrame) {
    return;
  }
  pointerAnimationFrame = window.requestAnimationFrame(() => {
    authPointer.value = { ...pendingAuthPointer };
    pointerAnimationFrame = 0;
  });
}

function stopPointerFrame() {
  if (!pointerAnimationFrame) {
    return;
  }
  window.cancelAnimationFrame(pointerAnimationFrame);
  pointerAnimationFrame = 0;
}
</script>

<template>
  <main
    class="experience-page"
    :style="authPageStyle"
    @pointermove="handleAuthPointerMove"
    @pointerleave="resetAuthPointer"
  >
    <div class="honeycomb-field" aria-hidden="true">
      <div class="honeycomb-grid"></div>
      <div class="honeycomb-spotlight"></div>
    </div>

    <Transition name="shell-transition" mode="out-in">
      <section key="auth" class="auth-page">
        <section class="auth-window">
          <div class="auth-logo" aria-hidden="true">
            <svg class="yinbo-logo" viewBox="0 0 32 32">
              <g transform="translate(1 -1) rotate(-45 16 16)">
                <path d="M16 4.8c6.2 0 11.2 5 11.2 11.2S22.2 27.2 16 27.2 4.8 22.2 4.8 16 9.8 4.8 16 4.8c4.5 0 8.1 3.6 8.1 8.1S20.5 21 16 21s-8.1-3.6-8.1-8.1S11.5 4.8 16 4.8c3 0 5.4 2.4 5.4 5.4s-2.4 5.4-5.4 5.4-5.4-2.4-5.4-5.4S13 4.8 16 4.8c1.7 0 3.1 1.4 3.1 3.1S17.7 11 16 11s-3.1-1.4-3.1-3.1" />
              </g>
            </svg>
          </div>
          <div class="auth-title">
            <span>yin-bo-agent</span>
          </div>

          <div class="auth-switch" :class="`is-${authMode}`" aria-label="认证方式">
            <span class="auth-switch-indicator" aria-hidden="true"></span>
            <button
              type="button"
              class="auth-switch-button"
              :class="{ active: authMode === 'login' }"
              @click="authMode = 'login'"
            >
              登录
            </button>
            <button
              type="button"
              class="auth-switch-button"
              :class="{ active: authMode === 'register' }"
              @click="authMode = 'register'"
            >
              注册
            </button>
          </div>

          <form class="auth-form" @submit.prevent="submitAuthForm">
            <label for="username">用户名</label>
            <input id="username" v-model="authForm.username" type="text" autocomplete="username" />

            <label for="password">密码</label>
            <div class="password-field">
              <input
                id="password"
                v-model="authForm.password"
                :type="isPasswordVisible ? 'text' : 'password'"
                :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'"
              />
              <button
                type="button"
                class="password-toggle"
                :class="{ visible: isPasswordVisible }"
                :aria-label="isPasswordVisible ? '隐藏密码' : '显示密码'"
                :title="isPasswordVisible ? '隐藏密码' : '显示密码'"
                @click="togglePasswordVisibility"
              >
                <svg
                  class="password-icon password-icon-eye"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    d="M2.25 12s3.75-6.75 9.75-6.75S21.75 12 21.75 12s-3.75 6.75-9.75 6.75S2.25 12 2.25 12Z"
                  />
                  <circle cx="12" cy="12" r="3.25" />
                </svg>
                <svg
                  class="password-icon password-icon-eye-off"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M3 3l18 18" />
                  <path
                    d="M10.58 5.14A10.76 10.76 0 0 1 12 5.25c6 0 9.75 6.75 9.75 6.75a18.72 18.72 0 0 1-4.1 4.94M6.61 6.61A19.13 19.13 0 0 0 2.25 12S6 18.75 12 18.75a10.9 10.9 0 0 0 3.42-.53M9.88 9.88A3 3 0 0 0 9 12a3 3 0 0 0 4.12 2.79"
                  />
                </svg>
              </button>
            </div>

            <div class="auth-feedback" :class="{ 'has-error': Boolean(loginError) }">
              <p v-if="loginError" class="error-text">{{ loginError }}</p>
            </div>

            <button class="auth-submit" type="submit" :disabled="isAuthSubmitting">
              {{ authSubmitText }}
            </button>
          </form>
        </section>
      </section>
    </Transition>
  </main>
</template>
