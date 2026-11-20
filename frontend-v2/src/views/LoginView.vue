<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getCaptcha } from '../api/auth'
import XInput from '../components/XInput.vue'
import XButton from '../components/XButton.vue'
import XIcon from '../components/XIcon.vue'
import logo from '../assets/logo.png'

/**
 * 登录/注册页：复刻老前端经典风格（居中白卡 + 登录/注册 tab + 蓝主按钮），
 * 不随主题切换（设计决定：登录页统一经典视觉）。
 */
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const tab = ref('login')
const loading = ref(false)

const loginForm = ref({ account: '', password: '' })
const loginErr = ref('')
const showLoginPwd = ref(false)

const regForm = ref({ account: '', userName: '', password: '', checkPassword: '', captchaAnswer: '' })
const regErr = ref('')
const showRegPwd = ref(false)
const showRegPwd2 = ref(false)

const captchaId = ref('')
const captchaImage = ref('')
const captchaSrc = computed(() => (captchaImage.value ? `data:image/png;base64,${captchaImage.value}` : ''))

async function refreshCaptcha() {
  try {
    const data = await getCaptcha()
    captchaId.value = data.challengeId
    captchaImage.value = data.imageBase64
    regForm.value.captchaAnswer = ''
  } catch (e) {
    captchaId.value = ''
    captchaImage.value = ''
  }
}
onMounted(refreshCaptcha)

function valid(err) {
  loginErr.value = err
  regErr.value = err
}

async function handleLogin() {
  valid('')
  if (!loginForm.value.account || !loginForm.value.password) {
    valid('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(loginForm.value.account, loginForm.value.password)
    router.push(route.query.redirect || '/knowledge')
  } catch (e) {
    valid(e.message)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  valid('')
  const f = regForm.value
  if (f.account.length < 4 || f.account.length > 32) { valid('账号需 4-32 位'); return }
  if (f.password.length < 8 || f.password.length > 32) { valid('密码需 8-32 位'); return }
  if (f.password !== f.checkPassword) { valid('两次输入的密码不一致'); return }
  if (!f.captchaAnswer) { valid('请输入验证码计算结果'); return }
  loading.value = true
  try {
    await auth.register({
      userAccount: f.account,
      userPassword: f.password,
      checkPassword: f.checkPassword,
      userName: f.userName,
      captchaId: captchaId.value,
      captchaAnswer: f.captchaAnswer,
    })
    tab.value = 'login'
    loginForm.value.account = f.account
    regErr.value = ''
  } catch (e) {
    valid(e.message)
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- 经典登录页（沿用老前端视觉），独立于主题系统 -->
  <div class="login-page">
    <div class="login-shell">
      <div class="login-brand">
        <img class="brand-logo" :src="logo" alt="学智汇" />
        <h1>学智汇</h1>
        <p>AI RAG 工作台</p>
      </div>

      <div class="login-card">
        <div class="tabs">
          <button class="tab" :class="{ on: tab === 'login' }" @click="tab = 'login'">登录</button>
          <button class="tab" :class="{ on: tab === 'register' }" @click="tab = 'register'">注册</button>
        </div>

        <form v-if="tab === 'login'" class="form" @submit.prevent="handleLogin">
          <XInput v-model="loginForm.account" icon="user" placeholder="账号" />
          <XInput
            v-model="loginForm.password" type="password" icon="lock"
            placeholder="密码" v-model:show-pwd="showLoginPwd"
          />
          <div v-if="loginErr" class="field-err">{{ loginErr }}</div>
          <XButton type="primary" class="submit" :loading="loading">登 录</XButton>
        </form>

        <form v-else class="form" @submit.prevent="handleRegister">
          <XInput v-model="regForm.account" icon="user" placeholder="账号（4-32 位）" />
          <XInput v-model="regForm.userName" icon="postcard" placeholder="昵称（可选）" />
          <XInput
            v-model="regForm.password" type="password" icon="lock"
            placeholder="密码（8-32 位）" v-model:show-pwd="showRegPwd"
          />
          <XInput
            v-model="regForm.checkPassword" type="password" icon="lock"
            placeholder="确认密码" v-model:show-pwd="showRegPwd2" @enter="handleRegister"
          />
          <div class="captcha-row">
            <button type="button" class="captcha-img" :title="captchaImage ? '看不清？点击刷新' : '点击重试'" @click="refreshCaptcha">
              <img v-if="captchaImage" :src="captchaSrc" alt="验证码" />
              <XIcon v-else name="loading" :size="18" class="cap-loading" />
            </button>
            <XInput v-model="regForm.captchaAnswer" icon="key" placeholder="验证码计算结果" @enter="handleRegister" />
          </div>
          <div v-if="regErr" class="field-err">{{ regErr }}</div>
          <XButton type="primary" class="submit" :loading="loading">注 册</XButton>
        </form>
      </div>

      <div class="login-foot">© 2026 学智汇. All rights reserved.</div>
    </div>
  </div>
</template>

<style scoped>
/* 经典风格：浅灰蓝底 + 白卡 + 蓝主按钮，固定配色不随主题 */
.login-page {
  min-height: 100vh;
  display: flex; align-items: center; justify-content: center;
  padding: 32px 20px;
  background: #f6f8fa;
}
.login-shell { width: min(100%, 440px); display: flex; flex-direction: column; align-items: center; gap: 18px; }
.login-brand { text-align: center; }
.brand-logo { width: 64px; height: 64px; margin: 0 auto 10px; border-radius: 16px; }
.login-brand h1 { font-size: 22px; font-weight: 700; color: #0f172a; }
.login-brand p { margin-top: 6px; font-size: 13px; color: #94a3b8; }

.login-card {
  width: 100%; padding: 24px 32px 24px;
  background: #ffffff; border: 1px solid #e2e8f0; border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
}
.tabs { display: flex; margin-bottom: 20px; border-bottom: 1px solid #e2e8f0; }
.tab {
  flex: 1; padding: 10px 0; font-size: 15px; color: #64748b;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
}
.tab.on { color: #2563eb; font-weight: 600; border-bottom-color: #2563eb; }

.form { display: flex; flex-direction: column; gap: 14px; }
.form .field-err { margin: 0; }
/* 经典页内输入框走固定蓝白配色 */
.form :deep(.x-input) { background: #ffffff; border-color: #e2e8f0; }
.form :deep(.x-input:focus-within) { border-color: #2563eb; box-shadow: 0 0 0 4px rgba(37, 99, 235, .12); }
.form :deep(.x-input input) { color: #0f172a; }
.form :deep(.x-input .prefix) { color: #94a3b8; }
.form :deep(.x-input:focus-within .prefix) { color: #2563eb; }
.form :deep(.x-input input::placeholder) { color: #b6c2d1; }

.submit { width: 100%; background: #2563eb; }
.submit:hover:not(:disabled) { background: #1d4ed8; }
.submit::before { display: none; }

.captcha-row { display: flex; gap: 10px; align-items: center; }
.captcha-img {
  flex: none; width: 132px; height: 42px;
  border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;
  background: #f4f4f4; display: flex; align-items: center; justify-content: center;
  cursor: pointer;
}
.captcha-img img { width: 100%; height: 100%; object-fit: cover; }
.cap-loading { color: #b6c2d1; animation: xspin 1s linear infinite; }

.login-foot { font-size: 12px; color: #94a3b8; }
</style>
