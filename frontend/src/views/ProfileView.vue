<template>
  <div class="profile-page" v-loading="loading">
    <template v-if="!authStore.isLogin">
      <section class="hero-bar">
        <div>
          <p class="hero-kicker">个人资料</p>
          <p class="hero-subtitle">登录后可以编辑头像、昵称和简介。</p>
        </div>
        <el-button type="primary" :icon="User" @click="uiStore.openLogin()">登录</el-button>
      </section>

      <el-empty description="请先登录后查看个人资料" />
    </template>

    <template v-else>
      <section class="hero-bar">
        <div class="hero-title-block">
          <p class="hero-kicker">个人中心</p>
          <h2>{{ profileName }}</h2>
          <p class="hero-subtitle">{{ authStore.user?.userAccount }} · {{ roleLabel }}</p>
        </div>
        <div class="hero-actions">
          <el-tag effect="plain" type="success">{{ authStore.user?.userRole === 'admin' ? '管理员' : '成员' }}</el-tag>
          <el-tag effect="plain">创建于 {{ createdText }}</el-tag>
        </div>
      </section>

      <section class="summary-grid">
        <article class="summary-card profile-summary">
          <div class="avatar-block">
            <el-upload
              :show-file-list="false"
              :http-request="handleAvatarUpload"
              accept="image/*"
            >
              <div class="avatar-wrap">
                <el-avatar :size="92" class="avatar" :src="form.userAvatar || undefined">
                  {{ avatarText }}
                </el-avatar>
                <div class="avatar-mask">
                  <el-icon><Camera /></el-icon>
                  <span>更换头像</span>
                </div>
              </div>
            </el-upload>

            <div class="avatar-meta">
              <strong>{{ profileName }}</strong>
              <span>{{ authStore.user?.userAccount }}</span>
              <p>{{ form.userProfile || '还没有填写个人简介。' }}</p>
            </div>
          </div>
        </article>

        <article class="summary-card status-card">
          <div class="panel-head">
            <h3>账号概览</h3>
            <span>当前状态</span>
          </div>
          <div class="status-grid">
            <div class="status-pill">
              <span>账号</span>
              <strong>{{ authStore.user?.userAccount }}</strong>
            </div>
            <div class="status-pill">
              <span>身份</span>
              <strong>{{ roleLabel }}</strong>
            </div>
            <div class="status-pill">
              <span>昵称</span>
              <strong>{{ profileName }}</strong>
            </div>
            <div class="status-pill">
              <span>头像</span>
              <strong>{{ authStore.user?.userAvatar ? '已设置' : '未设置' }}</strong>
            </div>
          </div>
        </article>
      </section>

      <section class="panel-grid">
        <article class="panel">
          <div class="panel-head">
            <h3>基本资料</h3>
            <span>可直接编辑</span>
          </div>

          <el-form :model="form" label-position="top" class="profile-form">
            <el-form-item label="账号">
              <el-input :model-value="authStore.user?.userAccount" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input
                v-model="form.userName"
                maxlength="30"
                show-word-limit
                placeholder="请输入昵称"
              />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input
                v-model="form.userProfile"
                type="textarea"
                :rows="5"
                maxlength="200"
                show-word-limit
                placeholder="写点介绍自己的话"
              />
            </el-form-item>
          </el-form>

          <div class="panel-actions">
            <el-button :icon="RefreshLeft" @click="resetForm">重置</el-button>
            <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">
              保存资料
            </el-button>
          </div>
        </article>

        <article class="panel">
          <div class="panel-head">
            <h3>资料补充</h3>
            <span>本地偏好</span>
          </div>

          <div class="setting-list">
            <div class="setting-row">
              <div class="setting-copy">
                <strong>默认入库</strong>
                <p>上传文档后自动进入向量化流程。</p>
              </div>
              <el-tag effect="plain" :type="settings.defaultVectorize ? 'success' : 'info'">
                {{ settings.defaultVectorize ? '开启' : '关闭' }}
              </el-tag>
            </div>

            <div class="setting-row">
              <div class="setting-copy">
                <strong>引用折叠</strong>
                <p>控制对话引用是否默认收起。</p>
              </div>
              <el-tag effect="plain" :type="settings.collapseRefs ? 'warning' : 'success'">
                {{ settings.collapseRefs ? '收起' : '展开' }}
              </el-tag>
            </div>
          </div>

          <el-divider />

          <div class="notice-card">
            <strong>支持信息</strong>
            <p>如遇到资料上传或头像更新异常，请先检查图片格式和网络连接。</p>
          </div>
        </article>
      </section>

      <section class="panel-grid bottom-grid">
        <article class="panel">
          <div class="panel-head">
            <h3>头像管理</h3>
            <span>上传与清空</span>
          </div>

          <div class="avatar-tools">
            <el-upload
              :show-file-list="false"
              :http-request="handleAvatarUpload"
              accept="image/*"
            >
              <el-button type="primary" :icon="Upload">上传头像</el-button>
            </el-upload>
            <el-button :icon="Delete" :disabled="!form.userAvatar" @click="clearAvatar">清空头像</el-button>
          </div>

          <div class="avatar-preview">
            <el-avatar :size="64" class="avatar-large" :src="form.userAvatar || undefined">
              {{ avatarText }}
            </el-avatar>
            <div class="avatar-preview-copy">
              <strong>预览</strong>
              <span>{{ form.userAvatar || '未设置头像' }}</span>
            </div>
          </div>
        </article>

        <article class="panel">
          <div class="panel-head">
            <h3>修改密码</h3>
            <span>安全验证</span>
          </div>

          <el-form :model="passwordForm" label-position="top" class="profile-form">
            <el-form-item label="原密码">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                show-password
                :prefix-icon="Lock"
                placeholder="请输入当前密码"
              />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                show-password
                :prefix-icon="Lock"
                placeholder="8-32 位新密码"
              />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input
                v-model="passwordForm.checkPassword"
                type="password"
                show-password
                :prefix-icon="Lock"
                placeholder="再次输入新密码"
              />
            </el-form-item>
          </el-form>

          <div class="panel-actions">
            <el-button :icon="RefreshLeft" @click="resetPasswordForm">重置</el-button>
            <el-button type="primary" :icon="Lock" :loading="passwordSaving" @click="handleChangePassword">
              修改密码
            </el-button>
          </div>

          <div class="notice-card">
            <strong>说明</strong>
            <p>修改成功后会立即切换到新会话。</p>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, Check, Delete, Lock, RefreshLeft, Upload, User } from '@element-plus/icons-vue'
import { changePassword, uploadAvatar } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { getSettings } from '../api/settings'

const authStore = useAuthStore()
const uiStore = useUiStore()

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
const roleLabel = computed(() => (authStore.user?.userRole === 'admin' ? '管理员' : '普通成员'))
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
  gap: 18px;
}

.hero-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.hero-title-block h2 {
  margin: 4px 0 0;
  font-size: 28px;
  color: #0f172a;
}

.hero-kicker {
  font-size: 13px;
  font-weight: 600;
  color: #0f766e;
}

.hero-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.summary-grid,
.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.summary-card,
.panel {
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 24px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(16px);
}

.summary-card {
  min-height: 196px;
  padding: 22px;
}

.panel {
  min-height: 268px;
  padding: 20px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-head h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.panel-head span {
  font-size: 13px;
  color: #64748b;
}

.avatar-block {
  display: flex;
  gap: 18px;
  align-items: center;
}

.avatar-wrap {
  position: relative;
  cursor: pointer;
  line-height: 0;
}

.avatar {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  font-weight: 700;
  font-size: 34px;
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
  background: rgba(15, 23, 42, 0.48);
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
}

.avatar-meta {
  min-width: 0;
}

.avatar-meta strong {
  display: block;
  font-size: 18px;
  color: #0f172a;
}

.avatar-meta span {
  display: block;
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

.avatar-meta p {
  margin-top: 12px;
  max-width: 420px;
  color: #475569;
  line-height: 1.7;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.status-pill {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.status-pill span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.status-pill strong {
  display: block;
  margin-top: 6px;
  font-size: 15px;
  color: #0f172a;
}

.profile-form {
  display: grid;
  grid-template-columns: 1fr;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.setting-list,
.security-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.setting-row,
.security-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.setting-copy,
.security-row > div {
  min-width: 0;
}

.setting-copy strong,
.security-row strong {
  display: block;
  font-size: 14px;
  color: #0f172a;
}

.setting-copy p,
.security-row p {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.notice-card {
  padding: 4px 2px 0;
}

.notice-card strong {
  display: block;
  font-size: 14px;
  color: #0f172a;
}

.notice-card p {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.7;
}

.avatar-tools {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.avatar-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.avatar-large {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  font-weight: 700;
}

.avatar-preview-copy {
  min-width: 0;
}

.avatar-preview-copy strong {
  display: block;
  font-size: 14px;
  color: #0f172a;
}

.avatar-preview-copy span {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
  word-break: break-all;
}

.bottom-grid {
  align-items: start;
}

@media (max-width: 1280px) {
  .summary-grid,
  .panel-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-bar,
  .avatar-block,
  .setting-row,
  .security-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .panel-actions {
    justify-content: stretch;
    flex-direction: column;
  }

  .panel-actions :deep(.el-button) {
    width: 100%;
  }

  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
