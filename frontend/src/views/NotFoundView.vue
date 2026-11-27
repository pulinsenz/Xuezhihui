<template>
  <div class="not-found-page">
    <div class="error-card">
      <div class="icon-circle">
        <span class="code-number">404</span>
      </div>

      <div class="text-block">
        <h2 class="error-title">抱歉，您访问的页面不存在</h2>
        <p class="error-desc">该链接可能已被移动、删除，或您输入的访问路径有误。请检查网址或返回系统主页。</p>
      </div>

      <div class="action-row">
        <el-button :icon="Back" class="back-btn" @click="handleGoBack">返回上一页</el-button>
        <el-button type="primary" :icon="HomeFilled" class="btn-brand" @click="handleGoHome">回到知识库首页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Back, HomeFilled } from '@element-plus/icons-vue'

const router = useRouter()

const handleGoBack = () => {
  if (router.options.history.state?.back) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}

const handleGoHome = () => {
  router.push('/knowledge')
}
</script>

<style scoped>
.not-found-page {
  min-height: min(calc(100dvh - 140px), 640px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  width: 100%;
}

.error-card {
  max-width: 520px;
  width: 100%;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: clamp(28px, 3.5vw, 48px) clamp(20px, 3vw, 36px);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.icon-circle {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: var(--tint);
  border: 1px solid var(--tint-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px var(--tint);
}

.code-number {
  font-size: var(--text-heading);
  font-weight: 800;
  color: var(--brand);
  letter-spacing: 0.05em;
  font-family: 'Space Grotesk', Consolas, sans-serif;
}

.text-block {
  margin-bottom: 24px;
}

.error-title {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.error-desc {
  margin: 10px auto 0;
  font-size: var(--text-body);
  color: var(--t3);
  line-height: 1.6;
  max-width: 400px;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.back-btn {
  border-radius: var(--r3);
  border-color: var(--line);
  min-height: 38px;
}

.btn-brand {
  background: var(--brand);
  border-color: var(--brand);
  border-radius: var(--r3);
  font-weight: 600;
  min-height: 38px;
  box-shadow: 0 2px 6px var(--tint-strong);
}

.btn-brand:hover {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
}
</style>
