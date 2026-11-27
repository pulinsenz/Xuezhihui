<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- 品牌标识与标语 -->
      <div class="login-brand">
        <div class="brand-badge-box">
          <img class="brand-logo" :src="logo" alt="学智汇" />
        </div>
        <h1 class="brand-title">学智汇</h1>
        <p class="brand-subtitle">AI-Native 学术智能知识库与检索增强问答平台</p>
      </div>

      <!-- 核心认证卡片 -->
      <div class="login-card">
        <AuthForm @success="handleSuccess" />
      </div>

      <!-- 页脚版权与学术声明 -->
      <footer class="login-foot">
        <span>© 2026 学智汇 AI Knowledge Hub. All rights reserved.</span>
        <span class="foot-tips">学术级多源向量检索与深度知识合成引擎</span>
      </footer>
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
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(20px, 3vw, 40px) 16px;
  background: radial-gradient(circle at 50% 20%, var(--tint) 0%, var(--bg) 70%);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(100%, 460px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(16px, 2vw, 24px);
}

.login-brand {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-badge-box {
  width: 72px;
  height: 72px;
  border-radius: var(--r1);
  background: var(--card);
  border: 1px solid var(--tint-strong);
  box-shadow: 0 4px 14px var(--tint);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.brand-logo {
  width: 48px;
  height: 48px;
  display: block;
  object-fit: contain;
}

.brand-title {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
  letter-spacing: -0.02em;
}

.brand-subtitle {
  margin: 6px 0 0;
  font-size: var(--text-body);
  color: var(--t3);
  line-height: 1.5;
}

.login-card {
  width: 100%;
  padding: clamp(24px, 2.5vw, 36px) clamp(22px, 2.5vw, 32px);
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  box-shadow: 0 8px 24px -4px color-mix(in srgb, var(--t1) 8%, transparent), 0 2px 6px -1px color-mix(in srgb, var(--t1) 4%, transparent);
}

.login-foot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: var(--text-muted);
  color: var(--t4);
  text-align: center;
}

.foot-tips {
  font-size: var(--text-muted);
  opacity: 0.8;
}

@media (max-height: 640px) {
  .login-page {
    padding: 16px;
  }
  .brand-badge-box {
    width: 56px;
    height: 56px;
    margin-bottom: 8px;
  }
  .brand-logo {
    width: 36px;
    height: 36px;
  }
  .login-shell {
    gap: 14px;
  }
}
</style>
