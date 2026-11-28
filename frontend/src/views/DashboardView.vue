<template>
  <div class="dashboard-page" v-loading="loading">
    <!-- 欢迎横幅：弹性流式 clamp，适配 80%~150% 浏览器缩放 -->
    <header class="welcome-card">
      <div class="welcome-main">
        <h2 class="welcome-title">亲爱的<i>「{{ welcomeName }}」</i>，欢迎回来</h2>
        <p class="welcome-line">{{ greetingLine }}</p>
      </div>
      <div class="welcome-date">
        <span class="date-big">{{ dateText }}</span>
        <span class="week-sub">{{ weekText }}</span>
      </div>
    </header>

    <!-- 统计指标网格：使用 auto-fit 智能重排，大比例缩放下自动转为 2 列平铺，杜绝挤压数字 -->
    <section class="stats-grid">
      <RouterLink v-for="card in statCards" :key="card.label" :to="card.to" class="stat-card">
        <div class="stat-info">
          <span class="stat-label">{{ card.label }}</span>
          <strong class="stat-value">{{ card.value }}</strong>
        </div>
        <div class="stat-icon-wrap">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
      </RouterLink>
    </section>

    <!-- 快捷入口卡片 -->
    <section class="panel-card shortcut-section">
      <div class="panel-head">
        <div class="panel-title-wrap">
          <span class="head-bar" />
          <h3>常用快捷入口</h3>
        </div>
      </div>
      <div class="shortcut-grid">
        <button
          v-for="action in quickActions"
          :key="action.label"
          type="button"
          class="shortcut-btn"
          @click="action.run()"
        >
          <div class="shortcut-icon-box">
            <el-icon><component :is="action.icon" /></el-icon>
          </div>
          <div class="shortcut-text">
            <span class="shortcut-label">{{ action.label }}</span>
            <span class="shortcut-sub">{{ action.sub || '快速开始' }}</span>
          </div>
          <el-icon class="shortcut-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>

    <!-- 下半双列流式面板：缩放或窄屏下平滑降级单列 -->
    <section class="panel-row-grid">
      <!-- 最近会话面板 -->
      <article class="panel-card">
        <div class="panel-head">
          <div class="panel-title-wrap">
            <span class="head-bar" />
            <h3>最近智能会话</h3>
          </div>
          <RouterLink
            v-if="recentSessions.length"
            class="panel-link"
            to="/chat"
          >
            <span>进入对话</span>
            <el-icon><ArrowRight /></el-icon>
          </RouterLink>
        </div>

        <div v-if="recentSessions.length" class="row-list custom-scroll">
          <button
            v-for="session in recentSessions"
            :key="session.session_id"
            type="button"
            class="list-row"
            @click="openSession(session.session_id)"
          >
            <div class="row-icon-box">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="row-main">
              <span class="row-title" :title="session.title">{{ session.title || '未命名会话' }}</span>
              <span class="row-date">{{ formatTime(session.update_time) }}</span>
            </div>
            <span class="msg-count-badge">{{ session.message_count }} 消息</span>
            <el-icon class="row-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
        <el-empty v-else :image-size="70" :description="authStore.isLogin ? '暂无历史会话' : '登录后可查看会话记录'" class="panel-empty" />
      </article>

      <!-- 知识库概览面板 -->
      <article class="panel-card">
        <div class="panel-head">
          <div class="panel-title-wrap">
            <span class="head-bar" />
            <h3>知识库快速概览</h3>
          </div>
          <RouterLink class="panel-link" to="/knowledge">
            <span>管理知识库</span>
            <el-icon><ArrowRight /></el-icon>
          </RouterLink>
        </div>

        <div v-if="knowledgePreview.length" class="row-list custom-scroll">
          <RouterLink
            v-for="item in knowledgePreview"
            :key="item.id"
            class="list-row"
            :to="{ name: 'KnowledgeDetail', params: { id: item.id }, query: { from: 'mine' } }"
          >
            <div class="row-cover-box">
              <img v-if="item.cover" :src="item.cover" class="row-cover" alt="封面" />
              <div v-else class="row-cover-letter">{{ (item.name || '知').charAt(0) }}</div>
            </div>
            <div class="row-main">
              <span class="row-title" :title="item.name">{{ item.name }}</span>
              <span class="tag-role" :class="item.roleClass">{{ item.roleLabel }}</span>
            </div>
            <span class="kb-docs-badge">{{ item.docCount || 0 }} 篇文档</span>
            <el-icon class="row-arrow"><ArrowRight /></el-icon>
          </RouterLink>
        </div>
        <el-empty v-else :image-size="70" :description="authStore.isLogin ? '还没有知识库，去新建一个吧' : '登录后可查看知识库'" class="panel-empty" />
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Bell, ChatDotRound, Collection, Folder, Plus } from '@element-plus/icons-vue'
import { listMyKnowledge } from '../api/knowledge'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { useMessageStore } from '../stores/messages'
import { useTabRefresh } from '../utils/tabRefresh'

const router = useRouter()
const authStore = useAuthStore()
const chat = useChatStore()
const messageStore = useMessageStore()

const loading = ref(false)
const knowledgeList = ref([])

const loadOverview = useTabRefresh(async (quiet, isCurrent) => {
  if (!authStore.isLogin) {
    knowledgeList.value = []
    chat.sessions = []
    messageStore.clear()
    return
  }

  loading.value = !quiet
  try {
    const results = await Promise.allSettled([
      chat.loadSessions(),
      messageStore.loadInvitations(),
      listMyKnowledge().then((list) => {
        if (isCurrent()) knowledgeList.value = list
      }),
    ])
    return results.every((result) => result.status === 'fulfilled')
  } finally {
    if (isCurrent()) loading.value = false
  }
}, ['list'])

watch(
  () => authStore.isLogin,
  () => loadOverview(),
  { immediate: true },
)

const knowledgeCount = computed(() => knowledgeList.value.length)
const memberCount = computed(() => knowledgeList.value.filter((item) => item.isMember).length)
const sessionCount = computed(() => chat.sessions.length)
const recentSessions = computed(() => chat.sessions.slice(0, 5))

const knowledgePreview = computed(() =>
  knowledgeList.value.slice(0, 5).map((item) => ({
    ...item,
    roleLabel: item.isOwner ? '我的' : item.isMember ? '协作' : '已收藏',
    roleClass: item.isOwner ? 'role-mine' : item.isMember ? 'role-collab' : 'role-fav',
  })),
)

const statCards = computed(() => [
  { label: '我的知识库', value: knowledgeCount.value, icon: Folder, to: '/knowledge' },
  { label: '对话会话数', value: sessionCount.value, icon: ChatDotRound, to: '/chat' },
  { label: '未读邀请通知', value: messageStore.unreadCount, icon: Bell, to: '/messages' },
  { label: '参与协作库', value: memberCount.value, icon: Collection, to: '/knowledge' },
])

const GREETING_BUCKETS = [
  { from: 5, to: 9, lines: ['早上好，新的一天从一次高效的知识检索开始。', '清晨好，您的学术知识库已经就绪。'] },
  { from: 9, to: 12, lines: ['上午精力充沛，适合研读核心文献与专业论文。', '上午好，把关键问题交给 AI 知识助手加速吧。'] },
  { from: 12, to: 18, lines: ['午后好，稍作休息后继续探索学术前沿。', '下午好，把这段时间留给深度思考与沉淀。'] },
  { from: 18, to: 24, lines: ['晚上好，今天辛苦了，温故而知新。', '夜色正好，来复盘一下今天的研究收获。'] },
  { from: 0, to: 5, lines: ['夜深了，注意保护视力与休息，学智汇伴你慢慢积累。', '凌晨安静，灵感常在专注时迸发。'] },
]

const greetingLine = ref('')
const welcomeName = computed(
  () => authStore.user?.userName?.trim() || authStore.user?.userAccount || '学者',
)
const now = new Date()
const dateText = `${now.getMonth() + 1}月${now.getDate()}日`
const weekText = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][now.getDay()]

const setGreeting = () => {
  const hour = new Date().getHours()
  const bucket = GREETING_BUCKETS.find((item) => hour >= item.from && hour < item.to)
  greetingLine.value = bucket.lines[Math.floor(Math.random() * bucket.lines.length)]
}
setGreeting()

const quickActions = computed(() => [
  { label: '发起 AI 检索对话', sub: '基于 RAG 混合知识检索问答', icon: ChatDotRound, run: () => openRoute('/chat') },
  { label: '新建专业知识库', sub: '上传学术文档并向量化入库', icon: Plus, run: () => openRoute({ path: '/knowledge', query: { new: 1 } }) },
  { label: '浏览公共知识广场', sub: '发现全站共享的开放学科资料', icon: Collection, run: () => openRoute('/public-knowledge') },
])

const openRoute = (target) => {
  router.push(target)
}

const openSession = async (sessionId) => {
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
  gap: clamp(14px, 1.6vw, 22px);
  width: 100%;
}

/* 欢迎卡：Teal 优雅渐变 */
.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  background: linear-gradient(135deg, var(--tint) 0%, var(--card) 100%);
  border: 1px solid var(--tint-strong);
  border-radius: var(--r1);
  padding: clamp(16px, 2vw, 24px) clamp(18px, 2.4vw, 32px);
  box-shadow: var(--shadow-card);
}

.welcome-main {
  min-width: 0;
}

.welcome-title {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.welcome-title i {
  font-style: normal;
  color: var(--brand);
}

.welcome-line {
  margin: 6px 0 0;
  font-size: var(--text-body);
  color: var(--t2);
}

.welcome-date {
  text-align: right;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.date-big {
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
  font-variant-numeric: tabular-nums;
}

.week-sub {
  font-size: var(--text-muted);
  color: var(--t3);
  margin-top: 2px;
}

/* 统计卡：自适应弹性 auto-fit */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, clamp(200px, 20vw, 280px)), 1fr));
  gap: clamp(12px, 1.5vw, 18px);
}

.stat-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: clamp(14px, 1.5vw, 18px) clamp(16px, 1.8vw, 22px);
  box-shadow: var(--shadow-card);
  text-decoration: none;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: var(--tint-strong);
  box-shadow: var(--shadow-card-hover);
}

.stat-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat-label {
  font-size: var(--text-body);
  color: var(--t3);
  font-weight: 500;
}

.stat-value {
  margin-top: 4px;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
  font-variant-numeric: tabular-nums;
}

.stat-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: var(--r3);
  background: var(--tint);
  color: var(--brand);
  border: 1px solid var(--tint-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-heading);
  flex-shrink: 0;
}

/* 统一面板卡片 */
.panel-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: clamp(16px, 1.8vw, 22px);
  box-shadow: var(--shadow-card);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.head-bar {
  width: 3.5px;
  height: 16px;
  border-radius: var(--r3);
  background: var(--brand);
}

.panel-head h3 {
  margin: 0;
  font-size: var(--text-body);
  font-weight: 700;
  color: var(--t1);
}

.panel-link {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--brand);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  cursor: pointer;
}

.panel-link:hover {
  color: var(--brand-hover);
}

/* 快捷入口网格 */
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, clamp(220px, 25vw, 320px)), 1fr));
  gap: clamp(10px, 1.2vw, 16px);
}

.shortcut-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--r3);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  width: 100%;
}

.shortcut-btn:hover {
  background: var(--tint);
  border-color: var(--tint-strong);
  transform: translateY(-1px);
}

.shortcut-icon-box {
  width: 36px;
  height: 36px;
  border-radius: var(--r3);
  background: var(--card);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-heading);
  border: 1px solid var(--line);
  flex-shrink: 0;
}

.shortcut-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.shortcut-label {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
}

.shortcut-sub {
  font-size: var(--text-muted);
  color: var(--t3);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shortcut-arrow {
  color: var(--t4);
  font-size: var(--text-body);
  flex-shrink: 0;
}

/* 下方两列布局 */
.panel-row-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: clamp(14px, 1.6vw, 22px);
}

.row-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.list-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--r3);
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
  text-decoration: none;
  transition: all 0.18s ease;
  width: 100%;
}

.list-row:hover {
  background: var(--bg);
  border-color: var(--line);
}

.row-icon-box {
  width: 32px;
  height: 32px;
  border-radius: var(--r3);
  background: color-mix(in srgb, var(--t4) 12%, transparent);
  color: var(--t3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-body);
  flex-shrink: 0;
}

.row-cover-box {
  width: 32px;
  height: 32px;
  border-radius: var(--r3);
  overflow: hidden;
  flex-shrink: 0;
}

.row-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.row-cover-letter {
  width: 100%;
  height: 100%;
  background: var(--brand);
  color: var(--card);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-body);
}

.row-main {
  flex: 1;
  min-width: 0;
}

.row-title {
  display: block;
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-date {
  display: block;
  font-size: var(--text-muted);
  color: var(--t4);
  margin-top: 2px;
}

.msg-count-badge,
.kb-docs-badge {
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--t3);
  background: color-mix(in srgb, var(--t4) 12%, transparent);
  padding: 2px 8px;
  border-radius: var(--r-pill);
  flex-shrink: 0;
}

.tag-role {
  display: inline-block;
  font-size: var(--text-muted);
  padding: 1px 6px;
  border-radius: var(--r3);
  margin-top: 3px;
  font-weight: 600;
}

.role-mine { background: var(--el-color-success-light-9); color: var(--el-color-success-dark-2); }
.role-collab,
.role-fav { background: var(--bg); color: var(--t3); }

.row-arrow {
  color: var(--t4);
  font-size: var(--text-muted);
  flex-shrink: 0;
}

.panel-empty {
  padding: 32px 12px;
}

@container (max-width: 760px) {
  .panel-row-grid {
    grid-template-columns: 1fr;
  }
}
</style>
