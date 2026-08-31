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
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(14, 165, 233, 0.08), transparent 36%),
    linear-gradient(225deg, rgba(20, 184, 166, 0.12), transparent 38%),
    linear-gradient(180deg, #f8fbfd 0%, #eef7f7 100%);
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(14, 165, 233, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(14, 165, 233, 0.05) 1px, transparent 1px);
  background-size: 64px 64px;
  opacity: 0.6;
  pointer-events: none;
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
  width: 74px;
  height: 74px;
  display: block;
  margin: 0 auto 10px;
  border-radius: 20px;
  box-shadow: 0 18px 32px rgba(20, 184, 166, 0.24);
}

.login-brand h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
}

.login-brand p {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}

.login-card {
  width: 100%;
  padding: 28px 32px 24px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 28px;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(18px);
}

.login-foot {
  font-size: 12px;
  color: #94a3b8;
}
</style>
