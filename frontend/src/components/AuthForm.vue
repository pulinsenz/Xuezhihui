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
        <!-- Cloudflare Turnstile 人机验证（注册防批量机器人）：配置 VITE_TURNSTILE_SITE_KEY 后渲染 -->
        <div v-if="turnstileEnabled" class="turnstile-wrap">
          <div ref="turnstileContainer" class="turnstile"></div>
        </div>
        <el-button type="primary" class="submit-btn" size="large" :loading="loading" @click="handleRegister">
          注 册
        </el-button>
      </el-form>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Postcard } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

/**
 * 登录 / 注册表单，供登录弹窗与登录页复用。
 * 登录成功后 emit('success')，导航等动作由父组件决定。
 *
 * 注册防刷：Cloudflare Turnstile 人机验证。
 * - 配置 VITE_TURNSTILE_SITE_KEY 后渲染 widget，token 随注册请求提交，后端 siteverify 校验；
 * - 未配置（本地开发）则跳过渲染、跳过传参，后端亦降级放行。
 */
const emit = defineEmits(['success'])
const authStore = useAuthStore()

const activeTab = ref('login')
const loading = ref(false)

const loginFormRef = ref()
const registerFormRef = ref()
const loginForm = reactive({ userAccount: '', userPassword: '' })
const registerForm = reactive({ userAccount: '', userName: '', userPassword: '', checkPassword: '' })

// ---------- Cloudflare Turnstile ----------
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY || ''
const turnstileEnabled = !!turnstileSiteKey
const turnstileContainer = ref(null)
const turnstileToken = ref('')
let turnstileWidgetId = null
let turnstileScriptPromise = null

/** 动态加载 Turnstile 官方脚本（仅启用时加载一次，避免拖慢无验证码环境首屏） */
function loadTurnstileScript() {
  if (window.turnstile) return Promise.resolve()
  if (!turnstileScriptPromise) {
    turnstileScriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
      script.async = true
      script.onload = resolve
      script.onerror = () => reject(new Error('Turnstile 脚本加载失败'))
      document.head.appendChild(script)
    })
  }
  return turnstileScriptPromise
}

async function initTurnstile() {
  if (!turnstileEnabled) return
  try {
    await loadTurnstileScript()
    turnstileWidgetId = window.turnstile.render(turnstileContainer.value, {
      sitekey: turnstileSiteKey,
      callback: (token) => { turnstileToken.value = token },
      'expired-callback': () => { turnstileToken.value = '' },
      'error-callback': () => { turnstileToken.value = '' },
    })
  } catch (e) {
    console.error('Turnstile 初始化失败', e)
  }
}

function destroyTurnstile() {
  if (turnstileWidgetId && window.turnstile) {
    try {
      window.turnstile.remove(turnstileWidgetId)
    } catch (e) {
      // 忽略销毁异常
    }
    turnstileWidgetId = null
  }
}

onMounted(initTurnstile)
onBeforeUnmount(destroyTurnstile)

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
  // 启用 Turnstile 时必须已完成人机验证（token 可能过期被清空，需重试）
  if (turnstileEnabled && !turnstileToken.value) {
    ElMessage.warning('请先完成人机验证')
    return
  }
  loading.value = true
  try {
    await authStore.register(
      registerForm.userAccount,
      registerForm.userPassword,
      registerForm.checkPassword,
      registerForm.userName,
      turnstileToken.value
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
.turnstile-wrap {
  display: flex;
  justify-content: center;
  margin: 12px 0 4px;
}
</style>
