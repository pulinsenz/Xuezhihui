<template>
  <div class="dashboard-page" v-loading="loading">
    <!-- 欢迎卡：问候语按 5 个时段各配 2 条随机取一，名字读登录用户昵称 -->
    <section class="welcome">
      <div class="welcome-main">
        <p class="welcome-title">亲爱的<i>「{{ welcomeName }}」</i>，欢迎使用学智汇</p>
        <p class="welcome-line">{{ greetingLine }}</p>
      </div>
      <div class="welcome-date">
        <strong>{{ dateText }}</strong>
        <small>{{ weekText }}</small>
      </div>
    </section>

    <section class="stats-grid">
      <RouterLink v-for="card in statCards" :key="card.label" :to="card.to" class="stat-card">
        <div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
        </div>
        <span class="stat-ghost"><el-icon><component :is="card.icon" /></el-icon></span>
      </RouterLink>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>快捷入口</h3>
      </div>
      <div class="shortcut-list">
        <button
          v-for="action in quickActions"
          :key="action.label"
          class="shortcut"
          @click="action.run()"
        >
          <span class="shortcut-icon"><el-icon><component :is="action.icon" /></el-icon></span>
          <span class="shortcut-label">{{ action.label }}</span>
          <el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>

    <section class="panel-grid">
      <article class="panel">
        <div class="panel-head">
          <h3>最近会话</h3>
          <el-link
            v-if="recentSessions.length"
            class="panel-link"
            :underline="false"
            @click="openRoute('/chat')"
          >
            查看全部<el-icon><ArrowRight /></el-icon>
          </el-link>
        </div>
        <div v-if="recentSessions.length" class="row-list">
          <button
            v-for="session in recentSessions"
            :key="session.session_id"
            class="row"
            @click="openSession(session.session_id)"
          >
            <span class="row-main">
              <span class="row-title">{{ session.title || '未命名会话' }}</span>
            </span>
            <span class="row-meta">{{ formatTime(session.update_time) }}<br />{{ session.message_count }} 条消息</span>
            <el-icon class="row-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
        <el-empty v-else :image-size="72" :description="authStore.isLogin ? '暂无会话' : '登录后可查看会话'" />
      </article>

      <article class="panel">
        <div class="panel-head">
          <h3>知识概览</h3>
          <RouterLink class="panel-link" to="/knowledge">
            管理知识库<el-icon><ArrowRight /></el-icon>
          </RouterLink>
        </div>
        <div v-if="knowledgePreview.length" class="row-list">
          <RouterLink
            v-for="item in knowledgePreview"
            :key="item.id"
            class="row"
            :to="{ name: 'KnowledgeDetail', params: { id: item.id }, query: { from: 'mine' } }"
          >
            <span class="row-main">
              <span class="row-title">{{ item.name }}</span>
              <span class="tag-role" :class="item.roleClass">{{ item.roleLabel }}</span>
            </span>
            <span class="kb-docs">{{ item.docCount || 0 }} 个文档</span>
            <el-icon class="row-arrow"><ArrowRight /></el-icon>
          </RouterLink>
        </div>
        <el-empty v-else :image-size="72" :description="authStore.isLogin ? '还没有知识库' : '登录后可查看知识库'" />
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Bell, ChatDotRound, Collection, Folder } from '@element-plus/icons-vue'
import { listMyKnowledge } from '../api/knowledge'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { useMessageStore } from '../stores/messages'

const router = useRouter()
const authStore = useAuthStore()
const chat = useChatStore()
const messageStore = useMessageStore()

const loading = ref(false)
const knowledgeList = ref([])

const loadOverview = async () => {
  if (!authStore.isLogin) {
    knowledgeList.value = []
    chat.sessions = []
    messageStore.clear()
    return
  }

  loading.value = true
  try {
    await Promise.allSettled([chat.loadSessions(), messageStore.loadInvitations(), loadKnowledge()])
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

watch(
  () => authStore.isLogin,
  () => {
    loadOverview().catch(() => {})
  },
  { immediate: true },
)

const knowledgeCount = computed(() => knowledgeList.value.length)
const memberCount = computed(() => knowledgeList.value.filter((item) => item.isMember).length)
const sessionCount = computed(() => chat.sessions.length)
const recentSessions = computed(() => chat.sessions.slice(0, 5))

const knowledgePreview = computed(() =>
  knowledgeList.value.slice(0, 4).map((item) => ({
    ...item,
    roleLabel: item.isOwner ? '自建' : item.isMember ? '协作' : '收藏',
    // 角色标签只有两档色：自建=品牌浅底，协作/收藏=中性灰（组 6：强调色只留一个）
    roleClass: item.isOwner ? 'is-brand' : 'is-gray',
  })),
)

const statCards = computed(() => [
  { label: '知识库', value: knowledgeCount.value, icon: Folder, to: '/knowledge' },
  { label: '会话', value: sessionCount.value, icon: ChatDotRound, to: '/chat' },
  { label: '未读邀请', value: messageStore.unreadCount, icon: Bell, to: '/messages' },
  { label: '协作中', value: memberCount.value, icon: Collection, to: '/knowledge' },
])

// ── 欢迎卡：时段问候（5 档 × 2 条随机）+ 昵称 + 日期 ──
const GREETING_BUCKETS = [
  { from: 5, to: 9, lines: ['早上好，新的一天从一次高效检索开始。', '清晨好，今天的知识库已经就绪。'] },
  { from: 9, to: 12, lines: ['上午精力正好，适合啃下最难的文档。', '上午好，把最重要的问题先问了。'] },
  { from: 12, to: 18, lines: ['午后好，稍作休息后继续探索吧。', '下午好，把这段下午留给深度思考。'] },
  { from: 18, to: 24, lines: ['晚上好，今天也辛苦了。', '夜色正好，来复盘一下今天的收获。'] },
  { from: 0, to: 5, lines: ['夜深了，注意休息，学智汇陪你慢慢来。', '凌晨安静，适合留一段独处的思考。'] },
]

const greetingLine = ref('')
const welcomeName = computed(
  () => authStore.user?.userName?.trim() || authStore.user?.userAccount || '朋友',
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
  { label: '发起 AI 对话', icon: ChatDotRound, run: () => openRoute('/chat') },
  { label: '新建知识库', icon: Folder, run: () => openRoute({ path: '/knowledge', query: { new: 1 } }) },
  { label: '浏览公开知识库', icon: Collection, run: () => openRoute('/public-knowledge') },
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
  --dashboard-gap: clamp(14px, 1.5cqi, 24px);
  display: flex;
  flex-direction: column;
  gap: var(--dashboard-gap);
}

/* 欢迎卡 */
.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--tint);
  border-radius: var(--r1);
  padding: 16px 20px;
}

.welcome-title {
  font-size: var(--text-heading);
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--t1);
}

.welcome-title i {
  font-style: normal;
  color: var(--brand);
}

.welcome-line {
  margin-top: 4px;
  font-size: var(--text-body);
  color: var(--t2);
}

.welcome-date {
  text-align: right;
  flex: none;
}

.welcome-date strong {
  display: block;
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
  font-variant-numeric: tabular-nums;
}

.welcome-date small {
  font-size: var(--text-muted);
  color: var(--t3);
}

/* 统计卡：数字优先 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--dashboard-gap);
}

.stat-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: var(--card);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
  padding: 12px 14px;
  transition: box-shadow 0.2s ease-out, transform 0.2s ease-out;
}

.stat-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.stat-ghost {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: var(--r3);
  background: var(--tint);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
}

.stat-label {
  font-size: var(--text-muted);
  color: var(--t3);
}

.stat-value {
  margin-top: 4px;
  font-size: var(--text-num);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--t1);
  font-variant-numeric: tabular-nums;
}

/* 区块 */
.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: var(--dashboard-gap);
}

.panel {
  background: var(--card);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
  min-width: 0;
  padding: 16px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.panel-head h3 {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
}

.panel-link {
  font-size: var(--text-muted);
  color: var(--brand);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
}

.panel-link .el-icon {
  font-size: 12px;
}

.panel-link:hover {
  color: var(--brand-hover);
}

/* 行列表：分隔线画在行与行之间，hover 浅底 */
.row-list {
  display: flex;
  flex-direction: column;
}

.row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 7px 4px;
  border-radius: var(--r3);
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease-out;
}

.row + .row {
  border-top: 1px solid var(--line);
}

.row:hover {
  background: var(--bg);
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
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-meta {
  display: block;
  text-align: right;
  white-space: nowrap;
  font-size: var(--text-muted);
  color: var(--t3);
}

.row-arrow {
  color: var(--t4);
  font-size: 14px;
  flex: none;
}

/* 快捷入口磁贴 */
.shortcut-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(8px, 1cqi, 16px);
}

.shortcut {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border: 1px solid var(--line);
  border-radius: var(--r2);
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease-out;
}

.shortcut:hover {
  background: var(--bg);
}

.shortcut-icon {
  width: 30px;
  height: 30px;
  border-radius: var(--r3);
  background: var(--tint);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  flex: none;
}

.shortcut-label {
  flex: 1;
  font-size: var(--text-body);
  font-weight: 500;
  color: var(--t1);
}

.tag-role {
  display: inline-block;
  font-size: var(--text-muted);
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
}

.tag-role.is-brand {
  background: var(--tint);
  color: var(--brand);
  font-weight: 600;
}

.tag-role.is-gray {
  background: var(--bg);
  color: var(--t3);
}

.kb-docs {
  font-size: var(--text-muted);
  color: var(--t3);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

@container (max-width: 640px) {
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .panel-grid { grid-template-columns: 1fr; }
  .shortcut-icon { display: none; }
}

@container (max-width: 390px) {
  .shortcut-list { grid-template-columns: 1fr; }
  .shortcut-icon { display: flex; }
}
</style>
