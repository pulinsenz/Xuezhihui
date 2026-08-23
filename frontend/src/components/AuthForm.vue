<template>
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
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Postcard } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

/**
 * 登录 / 注册表单，供登录弹窗与登录页复用。
 * 登录成功后 emit('success')，导航等动作由父组件决定。
 */
const emit = defineEmits(['success'])
const authStore = useAuthStore()

const activeTab = ref('login')
const loading = ref(false)

const loginFormRef = ref()
const registerFormRef = ref()
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

const handleLogin = async () => {
  if (loading.value) return
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await authStore.login(loginForm.userAccount, loginForm.userPassword)
    ElMessage.success('登录成功')
    emit('success')
  } catch (e) {
    // 错误信息已由拦截器提示
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (loading.value) return
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return
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
.submit-btn {
  width: 100%;
  margin-top: 4px;
}
</style>
