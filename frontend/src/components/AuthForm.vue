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
        <!-- 注册算术验证码（自研，防批量机器人薅 LLM token）：点击图片刷新 -->
        <el-form-item prop="captchaAnswer">
          <div class="captcha-row">
            <div class="captcha-img" :title="captchaImage ? '看不清？点击图片刷新' : '验证码加载失败，点击重试'" @click="refreshCaptcha">
              <el-icon v-if="!captchaImage" class="captcha-img-loading"><Loading /></el-icon>
              <img v-else :src="captchaImageSrc" alt="验证码" />
            </div>
            <el-input v-model="captchaAnswer" placeholder="验证码计算结果" :prefix-icon="Key" @keyup.enter="handleRegister" />
          </div>
        </el-form-item>
        <el-button type="primary" class="submit-btn" size="large" :loading="loading" @click="handleRegister">
          注 册
        </el-button>
      </el-form>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Postcard, Key, Loading } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { getCaptcha } from '../api/auth'

/**
 * 登录 / 注册表单，供登录弹窗与登录页复用。
 * 登录成功后 emit('success')，导航等动作由父组件决定。
 *
 * 注册防刷：自研算术验证码（替代 Cloudflare Turnstile，国内服务器可用）。
 * 进入注册 tab 时向后端 GET /auth/captcha 拉取一道算术题图片 + challengeId，
 * 用户输入计算结果后随注册请求提交；后端一次性校验（答案存 Redis，GET+DEL 原子消费防重放）。
 */
const emit = defineEmits(['success'])
const authStore = useAuthStore()

const activeTab = ref('login')
const loading = ref(false)

const loginFormRef = ref()
const registerFormRef = ref()
const loginForm = reactive({ userAccount: '', userPassword: '' })
const registerForm = reactive({ userAccount: '', userName: '', userPassword: '', checkPassword: '' })

// ---------- 算术验证码 ----------
const captchaId = ref('')
const captchaImage = ref('') // base64
const captchaAnswer = ref('')
const captchaImageSrc = computed(() =>
  captchaImage.value ? `data:image/png;base64,${captchaImage.value}` : ''
)

/** 拉取一道新验证码（挑战 id 与图片），并清空已输入的答案 */
async function refreshCaptcha() {
  try {
    const data = await getCaptcha()
    captchaId.value = data.challengeId
    captchaImage.value = data.imageBase64
    captchaAnswer.value = ''
  } catch (e) {
    // 错误信息已由请求拦截器提示；清空状态，用户点击图片可重试
    captchaId.value = ''
    captchaImage.value = ''
    captchaAnswer.value = ''
  }
}

// 进入注册 tab 时拉取验证码，离开时清空（防止用旧验证码提交）
watch(activeTab, (tab) => {
  if (tab === 'register') {
    refreshCaptcha()
  } else {
    captchaId.value = ''
    captchaImage.value = ''
    captchaAnswer.value = ''
  }
}, { immediate: true })

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
  captchaAnswer: [
    {
      validator: (_, value, cb) =>
        !captchaId.value || (value && value.trim()) ? cb() : cb(new Error('请输入验证码计算结果')),
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
  // 验证码须已加载且答案非空（验证码可能仍在加载/刷新失败）
  if (!captchaId.value || !captchaAnswer.value.trim()) {
    ElMessage.warning('请先输入验证码计算结果')
    return
  }
  loading.value = true
  try {
    await authStore.register(
      registerForm.userAccount,
      registerForm.userPassword,
      registerForm.checkPassword,
      registerForm.userName,
      captchaId.value,
      captchaAnswer.value
    )
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.userAccount = registerForm.userAccount
    loginForm.userPassword = ''
  } catch (e) {
    // 注册失败（含验证码错误/过期）：旧验证码已被服务端消费，刷新一道新验证码
    refreshCaptcha()
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
.captcha-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.captcha-img {
  flex-shrink: 0;
  width: 164px;
  height: 52px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  background: #f4f4f4;
  display: flex;
  align-items: center;
  justify-content: center;
}
.captcha-img img {
  width: 100%;
  height: 100%;
  display: block;
}
.captcha-img-loading {
  color: var(--el-text-color-placeholder);
  font-size: 18px;
}
</style>
