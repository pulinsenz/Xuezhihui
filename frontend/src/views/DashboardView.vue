<template>
  <div class="dashboard-page" v-loading="loading">
    <section class="hero-bar">
      <div>
        <p class="hero-kicker">{{ authStore.isLogin ? '欢迎回来' : '先从这里开始' }}</p>
        <p class="hero-subtitle">
          {{ authStore.isLogin ? '查看知识库、会话和协作提醒。' : '登录后可以查看知识库、会话和协作消息。' }}
        </p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :icon="ChatDotRound" @click="openRoute('/chat')">开始对话</el-button>
        <el-button :icon="Folder" @click="openRoute('/knowledge', true)">我的知识库</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article v-for="card in statCards" :key="card.label" class="stat-card">
        <div class="stat-icon" :class="card.tone">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-copy">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-hint">{{ card.hint }}</div>
        </div>
      </article>
    </section>

    <section class="panel-grid">
      <article class="panel">
        <div class="panel-head">
          <h3>快捷入口</h3>
          <span>常用操作</span>
        </div>
        <div class="shortcut-grid">
          <button
            v-for="action in quickActions"
            :key="action.label"
            class="shortcut"
            @click="action.run()"
          >
            <el-icon><component :is="action.icon" /></el-icon>
            <span>{{ action.label }}</span>
          </button>
        </div>

        <div class="setting-strip">
          <div>
            <span>默认入库</span>
            <strong>{{ settings.defaultVectorize === 1 ? '开启' : '关闭' }}</strong>
          </div>
          <div>
            <span>引用折叠</span>
            <strong>{{ settings.collapseRefs === 1 ? '收起' : '展开' }}</strong>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h3>最近会话</h3>
          <span>{{ recentSessions.length }} 条</span>
        </div>
        <div v-if="recentSessions.length" class="session-list">
          <button
            v-for="session in recentSessions"
            :key="session.session_id"
            class="session-row"
            @click="openSession(session.session_id)"
          >
            <div class="session-main">
              <div class="session-title">{{ session.title || '未命名会话' }}</div>
              <div class="session-meta">
                {{ formatTime(session.update_time) }} · {{ session.message_count }} 条消息
              </div>
            </div>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <el-empty v-else :description="authStore.isLogin ? '暂无会话' : '登录后可查看会话'" />
      </article>
    </section>

    <section class="panel-grid bottom-grid">
      <article class="panel">
        <div class="panel-head">
          <h3>知识概览</h3>
          <span>{{ knowledgeCount }} 个</span>
        </div>
        <div v-if="knowledgePreview.length" class="knowledge-list">
          <div v-for="item in knowledgePreview" :key="item.id" class="knowledge-row">
            <div class="knowledge-main">
              <div class="knowledge-title">{{ item.name }}</div>
              <div class="knowledge-meta">{{ item.role }} · {{ item.docCount }} 个文档</div>
            </div>
            <el-tag size="small" effect="plain" :type="item.roleType">{{ item.roleLabel }}</el-tag>
          </div>
        </div>
        <el-empty v-else :description="authStore.isLogin ? '还没有知识库' : '登录后可查看知识库'" />
      </article>

      <article class="panel">
        <div class="panel-head">
          <h3>系统状态</h3>
          <span>偏好设置</span>
        </div>
        <div class="status-grid">
          <div class="status-pill">
            <span>当前身份</span>
            <strong>{{ statusLabel }}</strong>
          </div>
          <div class="status-pill">
            <span>会话</span>
            <strong>{{ sessionCount }}</strong>
          </div>
          <div class="status-pill">
            <span>未读邀请</span>
            <strong>{{ messageStore.unreadCount }}</strong>
          </div>
          <div class="status-pill">
            <span>协作中</span>
            <strong>{{ memberCount }}</strong>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Bell, ChatDotRound, Collection, Folder, Setting, User } from '@element-plus/icons-vue'
import { getSettings } from '../api/settings'
import { listMyKnowledge } from '../api/knowledge'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { useMessageStore } from '../stores/messages'
import { useUiStore } from '../stores/ui'

const router = useRouter()
const authStore = useAuthStore()
const chat = useChatStore()
const messageStore = useMessageStore()
const uiStore = useUiStore()

const loading = ref(false)
const knowledgeList = ref([])
const settings = reactive({ defaultVectorize: 1, collapseRefs: 1 })

const loadOverview = async () => {
  if (!authStore.isLogin) {
    knowledgeList.value = []
    chat.sessions = []
    messageStore.clear()
    settings.defaultVectorize = 1
    settings.collapseRefs = 1
    return
  }

  loading.value = true
  try {
    await Promise.allSettled([chat.loadSessions(), messageStore.loadInvitations(), loadKnowledge(), loadSystemSettings()])
  } finally {
    loading.value = false
  }
}

const loadKnowledge = async () => {
  try {
    knowledgeList.value = await listMyKnowledge()
  } catch {
    knowledgeList.value = []
  }
}

const loadSystemSettings = async () => {
  try {
    const data = await getSettings()
    settings.defaultVectorize = data.defaultVectorize ?? 1
    settings.collapseRefs = data.collapseRefs ?? 1
  } catch {
    settings.defaultVectorize = 1
    settings.collapseRefs = 1
  }
}

watch(
  () => authStore.isLogin,
  () => {
    loadOverview().catch(() => {})
  },
  { immediate: true },
)

const knowledgeCount = computed(() => knowledgeList.value.length)
const ownerCount = computed(() => knowledgeList.value.filter((item) => item.isOwner).length)
const memberCount = computed(() => knowledgeList.value.filter((item) => item.isMember).length)
const publicCount = computed(() => knowledgeList.value.filter((item) => item.isPublic === 1).length)
const sessionCount = computed(() => chat.sessions.length)
const recentSessions = computed(() => chat.sessions.slice(0, 5))

const knowledgePreview = computed(() =>
  knowledgeList.value.slice(0, 4).map((item) => ({
    ...item,
    role: item.isOwner ? '自建' : item.isMember ? '协作' : '收藏',
    roleLabel: item.isOwner ? '自建' : item.isMember ? '协作' : '收藏',
    roleType: item.isOwner ? 'success' : item.isMember ? 'primary' : 'warning',
  })),
)

const statCards = computed(() => [
  {
    label: '知识库',
    value: knowledgeCount.value,
    hint: `${ownerCount.value} 个自建 · ${publicCount.value} 个公开`,
    icon: Folder,
    tone: 'mint',
  },
  {
    label: '会话',
    value: sessionCount.value,
    hint: authStore.isLogin ? '最近的聊天记录' : '登录后可查看',
    icon: ChatDotRound,
    tone: 'blue',
  },
  {
    label: '未读邀请',
    value: messageStore.unreadCount,
    hint: '知识协作和消息提醒',
    icon: Bell,
    tone: 'violet',
  },
  {
    label: '协作中',
    value: memberCount.value,
    hint: authStore.isLogin ? '当前可编辑的协作知识库' : '登录后可查看',
    icon: Collection,
    tone: 'amber',
  },
])

const statusLabel = computed(() => {
  if (!authStore.isLogin) return '访客'
  return authStore.isAdmin ? '管理员' : '成员'
})

const quickActions = computed(() => [
  { label: '对话', icon: ChatDotRound, run: () => openRoute('/chat') },
  { label: '知识库', icon: Folder, run: () => openRoute('/knowledge', true) },
  { label: '公开知识库', icon: Collection, run: () => openRoute('/public-knowledge') },
  { label: '消息', icon: Bell, run: () => openRoute('/messages', true) },
  { label: '设置', icon: Setting, run: () => openRoute('/settings', true) },
  { label: '个人资料', icon: User, run: () => openRoute('/profile', true) },
])

const openRoute = (path, needsLogin = false) => {
  if (needsLogin && !authStore.isLogin) {
    uiStore.openLogin()
    return
  }
  router.push(path)
}

const openSession = async (sessionId) => {
  if (!authStore.isLogin) {
    uiStore.openLogin()
    return
  }
  await chat.openSession(sessionId)
  router.push(`/chat/${sessionId}`)
}

const formatTime = (epochSec) => {
  if (!epochSec) return ''
  const date = new Date(Number(epochSec) * 1000)
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  if (date.toDateString() === now.toDateString()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return `${date.getMonth() + 1}月${date.getDate()}日`
}
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 4px 2px;
}

.hero-kicker {
  font-size: 13px;
  color: #0f766e;
  font-weight: 600;
}

.hero-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card,
.panel {
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 24px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(16px);
}

.stat-card {
  min-height: 146px;
  display: flex;
  gap: 14px;
  padding: 22px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 22px;
}

.stat-icon.mint {
  background: rgba(20, 184, 166, 0.14);
  color: #0f766e;
}

.stat-icon.blue {
  background: rgba(59, 130, 246, 0.14);
  color: #2563eb;
}

.stat-icon.violet {
  background: rgba(168, 85, 247, 0.14);
  color: #7c3aed;
}

.stat-icon.amber {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.stat-copy {
  min-width: 0;
}

.stat-label {
  font-size: 14px;
  color: #64748b;
}

.stat-value {
  margin-top: 4px;
  font-size: 30px;
  font-weight: 700;
  color: #0f172a;
}

.stat-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
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
  font-size: 18px;
  color: #0f172a;
}

.panel-head span {
  font-size: 13px;
  color: #64748b;
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.shortcut {
  min-height: 78px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.shortcut:hover {
  transform: translateY(-1px);
  border-color: rgba(20, 184, 166, 0.32);
  box-shadow: 0 10px 24px rgba(20, 184, 166, 0.08);
}

.shortcut .el-icon {
  font-size: 16px;
  color: #0f766e;
}

.shortcut span {
  font-size: 14px;
  line-height: 1.2;
}

.setting-strip {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.setting-strip > div,
.status-pill {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.setting-strip span,
.status-pill span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.setting-strip strong,
.status-pill strong {
  display: block;
  margin-top: 6px;
  font-size: 15px;
  color: #0f172a;
}

.session-list,
.knowledge-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.session-row,
.knowledge-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.session-row:hover,
.knowledge-row:hover {
  border-color: rgba(20, 184, 166, 0.28);
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.05);
}

.session-main,
.knowledge-main {
  min-width: 0;
}

.session-title,
.knowledge-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.session-meta,
.knowledge-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.bottom-grid {
  align-items: start;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

@media (max-width: 1280px) {
  .stats-grid,
  .panel-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .hero-bar,
  .panel-grid,
  .stats-grid {
    grid-template-columns: 1fr;
    display: grid;
  }

  .shortcut-grid,
  .status-grid,
  .setting-strip {
    grid-template-columns: 1fr;
  }
}
</style>
