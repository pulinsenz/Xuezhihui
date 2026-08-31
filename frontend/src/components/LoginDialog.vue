<template>
  <el-dialog
    v-model="visible"
    title="登录学智汇"
    width="420px"
    align-center
    destroy-on-close
    class="login-dialog"
  >
    <div class="dialog-brand">
      <img class="brand-logo" :src="logo" alt="学智汇" />
      <p>AI RAG 工作台</p>
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
  width: 52px;
  height: 52px;
  margin: 0 auto 8px;
  display: block;
  border-radius: 16px;
  object-fit: cover;
  box-shadow: 0 12px 24px rgba(20, 184, 166, 0.22);
}

.dialog-brand p {
  font-size: 12px;
  color: #64748b;
}
</style>
