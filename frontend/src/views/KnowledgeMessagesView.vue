<template>
  <div class="messages-page">
    <header class="page-head">
      <div class="head-left">
        <h2 class="page-title">协作消息与邀请</h2>
      </div>
      <el-button :icon="Refresh" :loading="messageStore.loading" class="refresh-btn" @click="reload">刷新消息</el-button>
    </header>

    <!-- 弹性双栏容器：左侧侧栏比例收缩，在 125%~150% 缩放下不挤爆右侧聊天流 -->
    <div class="message-shell">
      <aside class="thread-list">
        <div class="thread-list-head">
          <div class="thread-head-title">
            <span>协作邀请列表</span>
            <el-tag size="small" effect="plain" type="primary" class="count-pill">{{ threads.length }}</el-tag>
          </div>
          <span v-if="messageStore.unreadCount > 0" class="unread-pill">{{ messageStore.unreadCount }} 未读</span>
        </div>

        <div class="thread-items-wrap custom-scroll">
          <button
            v-for="item in threads"
            :key="item.id"
            type="button"
            class="thread-item"
            :class="{ active: isActive(item), unread: isUnread(item) }"
            @click="openThread(item)"
          >
            <div class="avatar-wrap">
              <el-avatar :size="40" class="thread-avatar" :src="item.knowledgeCover || undefined">
                {{ avatarText(item.knowledgeName) }}
              </el-avatar>
              <span v-if="isUnread(item)" class="dot-unread" />
            </div>

            <div class="thread-body">
              <div class="thread-title">
                <span class="thread-name">{{ item.knowledgeName || '未命名知识库' }}</span>
                <el-tag size="small" :type="statusTagType(item.status)" effect="plain" class="status-tag">
                  {{ statusLabel(item.status) }}
                </el-tag>
              </div>
              <div class="thread-meta">
                <span class="thread-peer">{{ peerName(item) }}</span>
                <span class="thread-time">{{ formatTime(item.createTime) }}</span>
              </div>
              <div class="thread-snippet">{{ item.message }}</div>
            </div>
          </button>
        </div>

        <el-empty v-if="!threads.length" description="暂无协作邀请通知" class="thread-empty" />
      </aside>

      <!-- 右侧协作邀请处理对话卡片 -->
      <section class="conversation">
        <template v-if="activeInvitation">
          <div class="conversation-head">
            <el-avatar :size="44" class="conversation-avatar" :src="activeInvitation.knowledgeCover || undefined">
              {{ avatarText(activeInvitation.knowledgeName) }}
            </el-avatar>
            <div class="conversation-info">
              <div class="conversation-title">
                <h3>{{ activeInvitation.knowledgeName || '未命名知识库' }}</h3>
                <el-tag size="small" :type="statusTagType(activeInvitation.status)" effect="plain">
                  {{ statusLabel(activeInvitation.status) }}
                </el-tag>
              </div>
              <div class="conversation-subtitle">
                <span>邀请人：{{ peerName(activeInvitation) }}</span>
                <span class="status-dot-text" :class="{ 'is-unread': !activeInvitation.readTime }">
                  {{ activeInvitation.readTime ? '已查看' : '新未读' }}
                </span>
              </div>
            </div>

            <el-button
              v-if="activeInvitation.status === 'ACCEPTED'"
              type="primary"
              class="enter-kb-btn"
              :icon="Document"
              @click="goKnowledge(activeInvitation.knowledgeId)"
            >
              打开知识库
            </el-button>
          </div>

          <div class="chat-stream custom-scroll">
            <div class="bubble-row" :class="activeInvitation.direction">
              <el-avatar
                :size="40"
                class="bubble-avatar"
                :src="speakerAvatar(activeInvitation) || undefined"
              >
                {{ speakerInitial(activeInvitation) }}
              </el-avatar>
              <div class="bubble-stack">
                <div class="bubble">
                  <div class="bubble-title">{{ bubbleTitle(activeInvitation) }}</div>
                  <div class="bubble-text">{{ activeInvitation.message }}</div>
                </div>
                <div class="bubble-footer">
                  <span>发起时间：{{ formatTime(activeInvitation.createTime) }}</span>
                  <span v-if="activeInvitation.handleTime">· 处理时间：{{ formatTime(activeInvitation.handleTime) }}</span>
                </div>

                <div v-if="activeInvitation.direction === 'incoming' && activeInvitation.status === 'PENDING'" class="bubble-actions">
                  <el-button type="primary" :icon="Check" :loading="actioning" class="action-btn-brand" @click="acceptActive">
                    接受邀请
                  </el-button>
                  <el-button :icon="Close" :loading="actioning" class="action-btn-reject" @click="rejectActive">
                    拒绝邀请
                  </el-button>
                </div>
              </div>
            </div>

            <div class="system-note">
              <span>{{ systemNote(activeInvitation) }}</span>
            </div>
          </div>
        </template>

        <el-empty v-else description="请在左侧选择一条协作邀请查看详情" class="conversation-empty" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Close, Document, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useMessageStore } from '../stores/messages'

const authStore = useAuthStore()
const messageStore = useMessageStore()
const router = useRouter()

const activeInvitationId = ref(null)
const actioning = ref(false)
let refreshTimer = null

const threads = computed(() => messageStore.invitations)
const activeInvitation = computed(() => threads.value.find((item) => String(item.id) === String(activeInvitationId.value)) || threads.value[0] || null)

const reload = async () => {
  try {
    await messageStore.loadInvitations()
    if (!threads.value.length) {
      activeInvitationId.value = null
      return
    }
    const exists = activeInvitationId.value && threads.value.some((item) => String(item.id) === String(activeInvitationId.value))
    if (!exists) {
      activeInvitationId.value = threads.value[0].id
    }
    const current = threads.value.find((item) => String(item.id) === String(activeInvitationId.value))
    if (current && current.direction === 'incoming' && !current.readTime) {
      await messageStore.markRead(current.id)
    }
  } catch (err) {
    // 请求错误已由全局拦截器提示
  }
}

const openThread = async (item) => {
  activeInvitationId.value = item.id
  try {
    if (item.direction === 'incoming' && !item.readTime) {
      await messageStore.markRead(item.id)
    }
  } catch (err) {
    // 保持消息页可用
  }
}

const acceptActive = async () => {
  if (!activeInvitation.value) return
  actioning.value = true
  try {
    const knowledgeId = await messageStore.accept(activeInvitation.value.id)
    ElMessage.success('已接受邀请')
    if (knowledgeId) {
      router.push(`/knowledge/${knowledgeId}`)
    } else {
      await reload()
    }
  } catch (err) {
    // 全局拦截器已提示
  } finally {
    actioning.value = false
  }
}

const rejectActive = async () => {
  if (!activeInvitation.value) return
  actioning.value = true
  try {
    await messageStore.reject(activeInvitation.value.id)
    ElMessage.success('已拒绝邀请')
    await reload()
  } catch (err) {
    // 全局拦截器已提示
  } finally {
    actioning.value = false
  }
}

const goKnowledge = (knowledgeId) => {
  if (knowledgeId) {
    router.push(`/knowledge/${knowledgeId}`)
  }
}

const avatarText = (text) => (text || '?').charAt(0).toUpperCase()

const peerName = (item) => {
  if (!item) return ''
  return item.direction === 'incoming'
    ? item.inviterName || '对方'
    : item.targetUserName || '对方'
}

const speakerAvatar = (item) => {
  if (!item) return ''
  return item.direction === 'incoming'
    ? item.inviterAvatar || ''
    : authStore.user?.userAvatar || ''
}

const speakerInitial = (item) => {
  if (!item) return '?'
  return item.direction === 'incoming'
    ? avatarText(item.inviterName)
    : avatarText(authStore.user?.userName || authStore.user?.userAccount)
}

const bubbleTitle = (item) => {
  if (!item) return ''
  if (item.direction === 'incoming') {
    return `${peerName(item)} 发来协作邀请`
  }
  return '我发出的协作邀请'
}

const systemNote = (item) => {
  if (!item) return ''
  if (item.status === 'PENDING') return '等待对方处理中'
  if (item.status === 'ACCEPTED') return '对方已接受邀请，协作权限已生效'
  if (item.status === 'REJECTED') return '对方已拒绝邀请'
  return ''
}

const statusLabel = (status) => {
  if (status === 'PENDING') return '待处理'
  if (status === 'ACCEPTED') return '已接受'
  if (status === 'REJECTED') return '已拒绝'
  return status || '未知'
}

const statusTagType = (status) => {
  if (status === 'PENDING') return 'warning'
  if (status === 'ACCEPTED') return 'success'
  if (status === 'REJECTED') return 'info'
  return ''
}

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

const isActive = (item) => String(item.id) === String(activeInvitationId.value)
const isUnread = (item) => item.direction === 'incoming' && !item.readTime

onMounted(async () => {
  if (!authStore.isLogin) return
  await reload()
  refreshTimer = window.setInterval(() => {
    if (authStore.isLogin) {
      reload().catch(() => {})
    }
  }, 20000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.messages-page {
  display: flex;
  flex-direction: column;
  gap: clamp(12px, 1.5vw, 18px);
  width: 100%;
  height: calc(100dvh - 120px);
  min-height: 520px;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.head-left {
  display: flex;
  flex-direction: column;
}

.page-title {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.refresh-btn {
  border-radius: var(--r3);
}

/* 主双栏外壳 */
.message-shell {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: clamp(260px, 22vw, 340px) minmax(0, 1fr);
  gap: clamp(12px, 1.5vw, 18px);
  overflow: hidden;
}

.thread-list,
.conversation {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
  min-height: 0;
  overflow: hidden;
}

.thread-list {
  display: flex;
  flex-direction: column;
}

.thread-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
  flex-shrink: 0;
}

.thread-head-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--text-body);
  font-weight: 700;
  color: var(--t1);
}

.count-pill {
  border: none;
  background: var(--tint);
  color: var(--brand);
}

.unread-pill {
  font-size: var(--text-muted);
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--r-pill);
  background: color-mix(in srgb, var(--el-color-danger) 10%, transparent);
  color: var(--el-color-danger);
}

.thread-items-wrap {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.thread-item {
  display: flex;
  width: 100%;
  padding: 14px 16px;
  gap: 12px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.thread-item:hover {
  background: var(--bg);
}

.thread-item.active {
  background: var(--tint);
  border-left: 3px solid var(--brand);
}

.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.thread-avatar {
  background: var(--brand);
  color: var(--card);
  font-weight: 700;
}

.dot-unread {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-danger);
  border: 2px solid var(--card);
}

.thread-body {
  min-width: 0;
  flex: 1;
}

.thread-title,
.thread-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.thread-name {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thread-item.unread .thread-name {
  color: var(--brand);
  font-weight: 700;
}

.thread-peer {
  font-size: var(--text-muted);
  color: var(--t3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thread-time {
  font-size: var(--text-muted);
  color: var(--t4);
  flex-shrink: 0;
}

.thread-snippet {
  margin-top: 4px;
  font-size: var(--text-muted);
  color: var(--t3);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 详情卡片 */
.conversation {
  display: flex;
  flex-direction: column;
}

.conversation-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.conversation-avatar {
  background: var(--brand);
  color: var(--card);
  font-weight: 700;
  flex-shrink: 0;
}

.conversation-info {
  flex: 1;
  min-width: 0;
}

.conversation-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.conversation-title h3 {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.conversation-subtitle {
  margin-top: 3px;
  font-size: var(--text-muted);
  color: var(--t3);
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot-text {
  position: relative;
  padding-left: 10px;
}

.status-dot-text::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--el-color-success);
}

.status-dot-text.is-unread::before {
  background: var(--el-color-danger);
}

.enter-kb-btn {
  border-radius: var(--r3);
  background: var(--brand);
  border-color: var(--brand);
  font-weight: 600;
}

.chat-stream {
  flex: 1;
  min-height: 0;
  padding: clamp(16px, 2vw, 28px);
  overflow-y: auto;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.bubble-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
}

.bubble-row.outgoing {
  flex-direction: row-reverse;
}

.bubble-avatar {
  background: var(--brand);
  color: var(--card);
  font-weight: 700;
  flex-shrink: 0;
}

.bubble-stack {
  max-width: min(80%, 640px);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bubble {
  padding: 16px 18px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
}

.bubble-row.incoming .bubble {
  border-top-left-radius: 4px;
}

.bubble-row.outgoing .bubble {
  border-top-right-radius: 4px;
  background: var(--tint);
  border-color: var(--tint-strong);
}

.bubble-title {
  margin-bottom: 6px;
  font-size: var(--text-muted);
  font-weight: 700;
  color: var(--brand);
}

.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--t1);
  font-size: var(--text-body);
  line-height: 1.6;
}

.bubble-footer {
  font-size: var(--text-muted);
  color: var(--t4);
  padding: 0 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.bubble-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.action-btn-brand {
  background: var(--brand);
  border-color: var(--brand);
  font-weight: 600;
}

.system-note {
  padding: 12px;
  text-align: center;
}

.system-note span {
  display: inline-block;
  padding: 6px 14px;
  border-radius: var(--r-pill);
  background: var(--card);
  border: 1px solid var(--line);
  font-size: var(--text-muted);
  color: var(--t3);
  box-shadow: 0 1px 2px color-mix(in srgb, var(--t1) 3%, transparent);
}

.thread-empty,
.conversation-empty {
  margin: auto;
  padding: 40px 16px;
}

.custom-scroll::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.custom-scroll::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--t4) 30%, transparent);
  border-radius: var(--r-pill);
}
@container (max-width: 680px) {
  .message-shell {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(160px, 35%) minmax(0, 1fr);
  }

  .conversation-head {
    flex-wrap: wrap;
  }
}
</style>
