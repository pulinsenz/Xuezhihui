<template>
  <div class="messages-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">协同互动</p>
        <h2>消息与知识协作</h2>
        <p class="page-subtitle">知识库邀请会在这里以会话形式呈现，接受后即可共同编辑与管理文档</p>
      </div>
      <el-button :icon="Refresh" :loading="messageStore.loading" @click="reload">刷新</el-button>
    </div>

    <div v-if="!authStore.isLogin" class="empty-panel">
      <el-empty description="登录后查看邀请消息与协作提醒">
        <el-button type="primary" @click="uiStore.openLogin()">立即登录</el-button>
      </el-empty>
    </div>

    <div v-else class="message-shell">
      <aside class="thread-list">
        <div class="thread-list-head">
          <div class="thread-head-title">
            <span>协作邀请</span>
            <el-tag size="small" effect="plain" type="primary">{{ threads.length }}</el-tag>
          </div>
          <span v-if="messageStore.unreadCount > 0" class="unread-pill">{{ messageStore.unreadCount }} 未读</span>
        </div>

        <div class="thread-items-wrap">
          <button
            v-for="item in threads"
            :key="item.id"
            type="button"
            class="thread-item"
            :class="{ active: isActive(item), unread: isUnread(item) }"
            @click="openThread(item)"
          >
            <el-avatar :size="42" class="thread-avatar" :src="item.knowledgeCover || undefined">
              {{ avatarText(item.knowledgeName) }}
            </el-avatar>
            <div class="thread-body">
              <div class="thread-title">
                <span class="thread-name">{{ item.knowledgeName || '未命名知识库' }}</span>
                <el-tag size="small" :type="statusTagType(item.status)" effect="plain">
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

        <el-empty v-if="!threads.length" description="暂无邀请消息" class="thread-empty" />
      </aside>

      <section class="conversation">
        <template v-if="activeInvitation">
          <div class="conversation-head">
            <el-avatar :size="46" class="conversation-avatar" :src="activeInvitation.knowledgeCover || undefined">
              {{ avatarText(activeInvitation.knowledgeName) }}
            </el-avatar>
            <div class="conversation-info">
              <div class="conversation-title">
                <span>{{ activeInvitation.knowledgeName || '未命名知识库' }}</span>
                <el-tag size="small" :type="statusTagType(activeInvitation.status)" effect="plain">
                  {{ statusLabel(activeInvitation.status) }}
                </el-tag>
              </div>
              <div class="conversation-subtitle">
                <span>{{ peerName(activeInvitation) }}</span>
                <span class="status-dot-text" :class="{ 'is-unread': !activeInvitation.readTime }">
                  {{ activeInvitation.readTime ? '已读' : '未读' }}
                </span>
              </div>
            </div>
            <el-button
              v-if="activeInvitation.status === 'ACCEPTED'"
              type="primary"
              plain
              :icon="Document"
              @click="goKnowledge(activeInvitation.knowledgeId)"
            >
              打开知识库
            </el-button>
          </div>

          <div class="chat-stream">
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
                  <span>{{ formatTime(activeInvitation.createTime) }}</span>
                  <span v-if="activeInvitation.handleTime">处理于 {{ formatTime(activeInvitation.handleTime) }}</span>
                </div>
                <div v-if="activeInvitation.direction === 'incoming' && activeInvitation.status === 'PENDING'" class="bubble-actions">
                  <el-button type="primary" :icon="Check" :loading="actioning" @click="acceptActive">
                    接受邀请
                  </el-button>
                  <el-button :icon="Close" :loading="actioning" @click="rejectActive">
                    拒绝
                  </el-button>
                </div>
              </div>
            </div>

            <div class="system-note">
              <span>{{ systemNote(activeInvitation) }}</span>
            </div>
          </div>
        </template>

        <el-empty v-else description="选择一条邀请查看详情" class="conversation-empty" />
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
import { useUiStore } from '../stores/ui'

const authStore = useAuthStore()
const messageStore = useMessageStore()
const uiStore = useUiStore()
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
  if (!authStore.isLogin) {
    uiStore.openLogin()
    return
  }
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
  gap: 16px;
  min-height: calc(100vh - 120px);
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 2px;
}

.page-kicker {
  font-size: 13px;
  color: #0f766e;
  font-weight: 600;
}

.page-head h2 {
  margin: 4px 0 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.page-subtitle {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.empty-panel {
  min-height: 460px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 24px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(16px);
}

.message-shell {
  flex: 1;
  min-height: 520px;
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
}

.thread-list,
.conversation {
  min-height: 0;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 24px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(16px);
}

.thread-list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.thread-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}

.thread-head-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.unread-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 9999px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.thread-items-wrap {
  flex: 1;
  overflow-y: auto;
}

.thread-item {
  display: flex;
  width: 100%;
  padding: 16px 18px;
  gap: 14px;
  border: 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.thread-item:hover {
  background: rgba(241, 245, 249, 0.65);
}

.thread-item.active {
  background: rgba(20, 184, 166, 0.08);
  border-left: 3px solid #0f766e;
}

.thread-item.unread .thread-name {
  color: #0f766e;
  font-weight: 700;
}

.thread-avatar {
  flex: 0 0 auto;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  font-weight: 700;
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
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thread-peer {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.thread-time {
  font-size: 11.5px;
  color: #94a3b8;
}

.thread-snippet {
  margin-top: 6px;
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.conversation {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conversation-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 22px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}

.conversation-avatar {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  font-weight: 700;
}

.conversation-info {
  flex: 1;
  min-width: 0;
}

.conversation-title,
.conversation-subtitle {
  display: flex;
  align-items: center;
  gap: 10px;
}

.conversation-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.conversation-subtitle {
  margin-top: 4px;
  font-size: 12.5px;
  color: #64748b;
}

.status-dot-text {
  position: relative;
  padding-left: 12px;
}

.status-dot-text::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}

.status-dot-text.is-unread::before {
  background: #ef4444;
}

.chat-stream {
  flex: 1;
  min-height: 0;
  padding: 24px 22px;
  overflow-y: auto;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.6) 0%, rgba(255, 255, 255, 0.9) 100%);
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
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}

.bubble-stack {
  max-width: min(78%, 620px);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bubble {
  padding: 16px 20px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 20px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.05);
}

.bubble-row.incoming .bubble {
  border-top-left-radius: 6px;
}

.bubble-row.outgoing .bubble {
  border-top-right-radius: 6px;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.12), rgba(13, 148, 136, 0.06));
  border-color: rgba(20, 184, 166, 0.25);
}

.bubble-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #0f766e;
}

.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.7;
}

.bubble-footer,
.bubble-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.bubble-footer {
  font-size: 12px;
  color: #94a3b8;
  padding: 0 4px;
}

.bubble-actions {
  margin-top: 4px;
}

.system-note {
  padding: 12px;
  text-align: center;
}

.system-note span {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 9999px;
  background: rgba(241, 245, 249, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.16);
  font-size: 12px;
  color: #64748b;
}

.thread-empty,
.conversation-empty {
  margin: auto;
  padding: 40px 0;
}

@media (max-width: 1024px) {
  .message-shell {
    grid-template-columns: 1fr;
  }
  .thread-list {
    max-height: 320px;
  }
}
</style>