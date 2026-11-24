<template>
  <div class="login-page">
    <div class="login-shell">
      <div class="login-brand">
        <img class="brand-logo" :src="logo" alt="学智汇" />
        <h1>学智汇</h1>
        <p>AI RAG 工作台</p>
      </div>

      <div class="login-card">
        <AuthForm @success="handleSuccess" />
      </div>

      <div class="login-foot">© 2026 学智汇. All rights reserved.</div>
    </div>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import AuthForm from '../components/AuthForm.vue'
import logo from '../assets/logo.png'

const router = useRouter()
const route = useRoute()

const handleSuccess = () => {
  router.push(route.query.redirect || '/dashboard')
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: var(--bg);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(100%, 440px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
}

.login-brand {
  text-align: center;
}

.brand-logo {
  width: 64px;
  height: 64px;
  display: block;
  margin: 0 auto 10px;
  border-radius: 16px;
}

.login-brand h1 {
  margin: 0;
  font-size: var(--text-title);
  font-weight: 700;
  color: var(--t1);
}

.login-brand p {
  margin-top: 6px;
  font-size: var(--text-muted);
  color: var(--text-muted);
}

.login-card {
  width: 100%;
  padding: 28px 32px 24px;
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow-card);
}

.login-foot {
  font-size: 12px;
  color: var(--t4);
}
</style>
