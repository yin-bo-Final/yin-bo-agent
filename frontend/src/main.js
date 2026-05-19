import { createApp } from 'vue';
import App from './App.vue';
import './styles.css';

if (window.location.hostname === '127.0.0.1') {
  const canonicalUrl = new URL(window.location.href);
  canonicalUrl.hostname = 'localhost';
  window.location.replace(canonicalUrl.toString());
}

createApp(App).mount('#app');
