<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-logo">学</div>
        <h1>学智汇</h1>
        <p>多 Agent 协同 RAG 校园问答系统</p>
      </div>

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" size="large">
            <el-form-item prop="userAccount">
              <el-input v-model="loginForm.userAccount" placeholder="账号" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="userPassword">
              <el-input v-model="loginForm.userPassword" type="password" show-password placeholder="密码" :prefix-icon="Lock" @keyup.enter="handleLogin" />
            </el-form-item>
            <el-button type="primary" class="submit-btn" size="large" :loading="loading" @click="handleLogin">
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" size="large">
            <el-form-item prop="userAccount">
              <el-input v-model="registerForm.userAccount" placeholder="账号（4-32 位）" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="userName">
              <el-input v-model="registerForm.userName" placeholder="昵称（可选）" :prefix-icon="Postcard" />
            </el-form-item>
            <el-form-item prop="userPassword">
              <el-input v-model="registerForm.userPassword" type="password" show-password placeholder="密码（8-32 位）" :prefix-icon="Lock" />
            </el-form-item>
            <el-form-item prop="checkPassword">
              <el-input v-model="registerForm.checkPassword" type="password" show-password placeholder="确认密码" :prefix-icon="Lock" @keyup.enter="handleRegister" />
            </el-form-item>
            <el-button type="primary" class="submit-btn" size="large" :loading="loading" @click="handleRegister">
              注 册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Postcard } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeTab = ref('login')
const loading = ref(false)

const loginForm = reactive({ userAccount: '', userPassword: '' })
const registerForm = reactive({ userAccount: '', userName: '', userPassword: '', checkPassword: '' })

const loginRules = {
  userAccount: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  userPassword: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const registerRules = {
  userAccount: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 32, message: '账号长度 4-32 位', trigger: 'blur' },
  ],
  userPassword: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度 8-32 位', trigger: 'blur' },
  ],
  checkPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_, value, cb) =>
        value === registerForm.userPassword ? cb() : cb(new Error('两次输入的密码不一致')),
      trigger: 'blur',
    },
  ],
}

const redirectTo = () => route.query.redirect || '/knowledge'

const handleLogin = async () => {
  loading.value = true
  try {
    await authStore.login(loginForm.userAccount, loginForm.userPassword)
    ElMessage.success('登录成功')
    router.push(redirectTo())
  } catch (e) {
    // 错误信息已由拦截器提示
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  loading.value = true
  try {
    await authStore.register(
      registerForm.userAccount,
      registerForm.userPassword,
      registerForm.checkPassword,
      registerForm.userName
    )
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.userAccount = registerForm.userAccount
    loginForm.userPassword = ''
  } catch (e) {
    // 错误信息已由拦截器提示
  } finally {
    loading.value = false
  }
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
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  line-height: 48px;
  font-size: 26px;
  font-weight: 700;
  color: #fff;
  background: #409eff;
  border-radius: 10px;
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
.submit-btn {
  width: 100%;
  margin-top: 4px;
}
</style>
