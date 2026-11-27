<template>
  <div class="profile-page" v-loading="loading">
    <!-- 头部横幅卡片 -->
    <header class="profile-hero-card">
      <div class="hero-left">
        <h2 class="hero-name">{{ profileName }}</h2>
        <p class="hero-account">{{ authStore.user?.userAccount }} · {{ currentRoleLabel }}</p>
      </div>
      <div class="hero-actions">
        <el-tag effect="plain" type="success" class="role-tag">{{ currentRoleLabel }}</el-tag>
        <el-tag effect="plain" class="create-tag">注册于 {{ createdText }}</el-tag>
      </div>
    </header>

    <!-- 头像与账号概览：采用自适应 2 列，缩放下平滑降为 1 列 -->
    <section class="summary-grid">
      <!-- 个人头像与简介卡片 -->
      <article class="card-box avatar-summary-card">
        <div class="avatar-block">
          <el-upload
            :show-file-list="false"
            :http-request="handleAvatarUpload"
            accept="image/*"
            class="avatar-uploader"
          >
            <div class="avatar-wrap">
              <el-avatar :size="84" class="main-avatar" :src="form.userAvatar || undefined">
                {{ avatarText }}
              </el-avatar>
              <div class="avatar-mask">
                <el-icon><Camera /></el-icon>
                <span>更换头像</span>
              </div>
            </div>
          </el-upload>

          <div class="avatar-meta">
            <strong class="user-display-name">{{ profileName }}</strong>
            <span class="user-account-sub">{{ authStore.user?.userAccount }}</span>
            <p class="user-bio">{{ form.userProfile || '还没有填写个人简介。' }}</p>
            <el-button v-if="form.userAvatar" :icon="Delete" plain @click="clearAvatar">移除头像</el-button>
          </div>
        </div>
      </article>

      <!-- 账号状态卡片 -->
      <article class="card-box status-card">
        <div class="card-head">
          <h3>账号状态概览</h3>
        </div>
        <div class="status-pill-grid">
          <div class="status-pill">
            <span class="pill-label">登录账号</span>
            <strong class="pill-val">{{ authStore.user?.userAccount }}</strong>
          </div>
          <div class="status-pill">
            <span class="pill-label">系统身份</span>
            <strong class="pill-val">{{ currentRoleLabel }}</strong>
          </div>
          <div class="status-pill">
            <span class="pill-label">当前昵称</span>
            <strong class="pill-val">{{ profileName }}</strong>
          </div>
          <div class="status-pill">
            <span class="pill-label">头像状态</span>
            <strong class="pill-val">{{ authStore.user?.userAvatar ? '已自定义' : '默认首字' }}</strong>
          </div>
        </div>
        <dl class="profile-preferences">
          <dt>默认入库</dt>
          <dd>{{ settings.defaultVectorize ? '开启' : '关闭' }}</dd>
          <dt>引用折叠</dt>
          <dd>{{ settings.collapseRefs ? '收起' : '展开' }}</dd>
        </dl>
      </article>
    </section>

    <!-- 基本资料编辑与本地偏好 -->
    <section class="forms-grid">
      <!-- 基础资料表单卡片 -->
      <article class="card-box">
        <div class="card-head">
          <h3>编辑基本资料</h3>
        </div>

        <el-form :model="form" label-position="top" class="custom-form">
          <el-form-item label="登录账号 (只读)">
            <el-input :model-value="authStore.user?.userAccount" disabled />
          </el-form-item>
          <el-form-item label="用户昵称" required>
            <el-input
              v-model="form.userName"
              maxlength="30"
              show-word-limit
              placeholder="请输入您的昵称"
            />
          </el-form-item>
          <el-form-item label="个人学术简介">
            <el-input
              v-model="form.userProfile"
              type="textarea"
              :rows="4"
              maxlength="200"
              show-word-limit
              placeholder="写写关于您的学术方向或个人介绍..."
            />
          </el-form-item>
        </el-form>

        <div class="form-actions">
          <el-button :icon="RefreshLeft" @click="resetForm">重置修改</el-button>
          <el-button type="primary" :icon="Check" :loading="saving" class="btn-brand" @click="handleSave">
            保存资料
          </el-button>
        </div>
      </article>

      <!-- 密码修改安全验证卡片 -->
      <article class="card-box">
        <div class="card-head">
          <h3>修改登录密码</h3>
        </div>

        <el-form :model="passwordForm" label-position="top" class="custom-form">
          <el-form-item label="当前原密码" required>
            <el-input
              v-model="passwordForm.oldPassword"
              autocomplete="current-password"
              type="password"
              show-password
              :prefix-icon="Lock"
              placeholder="请输入当前使用的密码"
            />
          </el-form-item>
          <el-form-item label="设置新密码" required>
            <el-input
              v-model="passwordForm.newPassword"
              autocomplete="new-password"
              type="password"
              show-password
              :prefix-icon="Lock"
              placeholder="8-32 位新密码"
            />
          </el-form-item>
          <el-form-item label="确认新密码" required>
            <el-input
              v-model="passwordForm.checkPassword"
              autocomplete="new-password"
              type="password"
              show-password
              :prefix-icon="Lock"
              placeholder="请再次输入新密码"
            />
          </el-form-item>
        </el-form>

        <div class="form-actions">
          <el-button :icon="RefreshLeft" @click="resetPasswordForm">清空</el-button>
          <el-button type="primary" :icon="Lock" :loading="passwordSaving" class="btn-brand" @click="handleChangePassword">
            更新密码
          </el-button>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, Check, Delete, Lock, RefreshLeft } from '@element-plus/icons-vue'
import { changePassword, uploadAvatar } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { roleLabel } from '../utils/roles'
import { getSettings } from '../api/settings'

const authStore = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const settings = reactive({
  defaultVectorize: 1,
  collapseRefs: 1,
})
const form = reactive({
  userName: '',
  userProfile: '',
  userAvatar: '',
})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  checkPassword: '',
})
const snapshot = ref({
  userName: '',
  userProfile: '',
  userAvatar: '',
})

const profileName = computed(() => form.userName.trim() || authStore.user?.userName || authStore.user?.userAccount || '?')
const currentRoleLabel = computed(() => roleLabel(authStore.user?.userRole))
const avatarText = computed(() => profileName.value.charAt(0).toUpperCase())
const createdText = computed(() => {
  const value = authStore.user?.createTime
  if (!value) return '未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未知'
  return date.toLocaleDateString('zh-CN')
})

const syncFromUser = () => {
  form.userName = authStore.user?.userName || ''
  form.userProfile = authStore.user?.userProfile || ''
  form.userAvatar = authStore.user?.userAvatar || ''
  snapshot.value = { ...form }
}

const loadSettings = async () => {
  try {
    const data = await getSettings()
    settings.defaultVectorize = data.defaultVectorize ?? 1
    settings.collapseRefs = data.collapseRefs ?? 1
  } catch {
    settings.defaultVectorize = 1
    settings.collapseRefs = 1
  }
}

const handleAvatarUpload = async (options) => {
  try {
    const url = await uploadAvatar(options.file)
    form.userAvatar = url
    ElMessage.success('头像已更新，保存后生效')
  } catch {
    // 错误由拦截器统一提示
  }
}

const clearAvatar = () => {
  form.userAvatar = ''
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.checkPassword = ''
}

const resetForm = () => {
  Object.assign(form, snapshot.value)
}

const handleSave = async () => {
  if (!form.userName.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }
  saving.value = true
  try {
    await authStore.updateProfile({
      userName: form.userName.trim(),
      userAvatar: form.userAvatar || undefined,
      userProfile: form.userProfile.trim() || undefined,
    })
    ElMessage.success('个人资料已保存')
    syncFromUser()
  } catch {
    // 错误由拦截器统一提示
  } finally {
    saving.value = false
  }
}

const passwordSaving = ref(false)

const handleChangePassword = async () => {
  const oldPassword = passwordForm.oldPassword.trim()
  const newPassword = passwordForm.newPassword.trim()
  const checkPassword = passwordForm.checkPassword.trim()
  if (!oldPassword || !newPassword || !checkPassword) {
    ElMessage.warning('请完整填写原密码和新密码')
    return
  }
  if (newPassword.length < 8 || newPassword.length > 32) {
    ElMessage.warning('新密码长度应在 8-32 位')
    return
  }
  if (newPassword !== checkPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  passwordSaving.value = true
  try {
    const data = await changePassword({
      oldPassword,
      newPassword,
      checkPassword,
    })
    ElMessage.success('密码已修改')
    resetPasswordForm()
    authStore.setSession(data.token, data.user)
  } catch {
    // 错误由拦截器统一提示
  } finally {
    passwordSaving.value = false
  }
}

watch(
  () => authStore.isLogin,
  async (loggedIn) => {
    if (!loggedIn) return
    loading.value = true
    try {
      syncFromUser()
      resetPasswordForm()
      await loadSettings()
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

onMounted(() => {
  if (authStore.isLogin) {
    syncFromUser()
    resetPasswordForm()
    loadSettings().catch(() => {})
  }
})
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.6vw, 20px);
  width: 100%;
}

.profile-hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: clamp(16px, 2vw, 24px);
  box-shadow: var(--shadow-card);
}

.hero-name {
  margin: 4px 0 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.hero-account {
  margin: 4px 0 0;
  font-size: var(--text-body);
  color: var(--t3);
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-grid,
.forms-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: clamp(14px, 1.6vw, 20px);
}

.card-box {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: clamp(16px, 1.8vw, 22px);
  box-shadow: var(--shadow-card);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
}

.card-head h3 {
  margin: 0;
  font-size: var(--text-body);
  font-weight: 700;
  color: var(--t1);
}

.avatar-block {
  display: flex;
  gap: 18px;
  align-items: center;
}

.avatar-uploader {
  flex-shrink: 0;
}

.avatar-wrap {
  position: relative;
  cursor: pointer;
  line-height: 0;
  border-radius: 50%;
  overflow: hidden;
}

.main-avatar {
  background: var(--brand);
  color: var(--card);
  font-weight: 700;
  font-size: var(--text-num);
}

.avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: color-mix(in srgb, var(--t1) 55%, transparent);
  color: var(--card);
  font-size: var(--text-muted);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
}

.avatar-meta {
  min-width: 0;
  flex: 1;
}

.user-display-name {
  display: block;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.user-account-sub {
  display: block;
  margin-top: 3px;
  font-size: var(--text-muted);
  color: var(--t3);
}

.user-bio {
  margin: 10px 0 0;
  font-size: var(--text-body);
  color: var(--t2);
  line-height: 1.5;
}

.status-pill-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.status-pill {
  padding: 12px 14px;
  border-radius: var(--r3);
  background: var(--bg);
  border: 1px solid var(--line);
}

.pill-label {
  display: block;
  font-size: var(--text-muted);
  color: var(--t3);
}

.pill-val {
  display: block;
  margin-top: 4px;
  font-size: var(--text-body);
  color: var(--t1);
}

.custom-form {
  margin-bottom: 8px;
}

.form-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.btn-brand {
  background: var(--brand);
  border-color: var(--brand);
  font-weight: 600;
}

@container (max-width: 680px) {
  .summary-grid,
  .forms-grid {
    grid-template-columns: 1fr;
  }
}
.profile-preferences {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px 16px;
  margin-top: 16px;
  color: var(--t3);
  font-size: var(--text-body);
}

.profile-preferences dd {
  color: var(--t2);
}

.avatar-block {
  flex-wrap: wrap;
}

.avatar-meta {
  overflow-wrap: anywhere;
}
</style>
