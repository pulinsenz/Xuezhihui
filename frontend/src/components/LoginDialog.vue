<template>
  <el-dialog
    v-model="visible"
    title="登录学智汇"
    width="420px"
    align-center
    destroy-on-close
  >
    <div class="dialog-brand">
      <div class="brand-logo">学</div>
      <p>多 Agent 协同 RAG 校园问答系统</p>
    </div>
    <AuthForm @success="handleSuccess" />
  </el-dialog>
</template>

<script setup>
import AuthForm from './AuthForm.vue'

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
  width: 40px;
  height: 40px;
  margin: 0 auto 8px;
  line-height: 40px;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  background: #409eff;
  border-radius: 8px;
}
.dialog-brand p {
  font-size: 12px;
  color: #909399;
}
</style>
