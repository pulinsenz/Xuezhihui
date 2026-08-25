<template>
  <el-dialog
    v-model="visible"
    title="登录学智汇"
    width="420px"
    align-center
    destroy-on-close
  >
    <div class="dialog-brand">
      <img class="brand-logo" :src="logo" alt="学智汇" />
      <p>多 Agent 协同 RAG 校园问答系统</p>
    </div>
    <AuthForm @success="handleSuccess" />
  </el-dialog>
</template>

<script setup>
import AuthForm from './AuthForm.vue'
import logo from '../assets/logo.png'

const visible = defineModel({ type: Boolean, default: false })

const handleSuccess = () => {
  visible.value = false
}
</script>

<style scoped>
.dialog-brand {
  text-align: center;
  margin-bottom: 16px;
}
.brand-logo {
  width: 48px;
  height: 48px;
  margin: 0 auto 8px;
  display: block;
  border-radius: 8px;
  object-fit: contain;
}
.dialog-brand p {
  font-size: 12px;
  color: #909399;
}
</style>
