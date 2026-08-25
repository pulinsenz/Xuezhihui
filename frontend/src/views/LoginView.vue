<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <img class="brand-logo" :src="logo" alt="学智汇" />
        <h1>学智汇</h1>
        <p>多 Agent 协同 RAG 校园问答系统</p>
      </div>

      <AuthForm @success="handleSuccess" />
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
  router.push(route.query.redirect || '/chat')
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a93 0%, #409eff 100%);
}
.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 32px 36px 24px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}
.brand {
  text-align: center;
  margin-bottom: 20px;
}
.brand-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 12px;
  display: block;
  border-radius: 10px;
  object-fit: contain;
}
.brand h1 {
  font-size: 24px;
  color: #303133;
}
.brand p {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}
</style>
