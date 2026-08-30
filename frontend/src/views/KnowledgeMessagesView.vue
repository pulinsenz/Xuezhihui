<template>
  <div class="messages-page">
    <div class="page-head">
      <div>
        <h1>消息</h1>
        <p>知识库邀请会在这里以聊天流的形式出现，接受后即可获得共同编辑权限。</p>
      </div>
      <el-button :icon="Refresh" :loading="messageStore.loading" @click="reload">刷新</el-button>
    </div>

    <div v-if="!authStore.isLogin" class="empty-panel">
      <el-empty description="登录后查看邀请消息">
        <el-button type="primary" @click="uiStore.openLogin()">登录</el-button>
      </el-empty>
    </div>

    <div v-else class="message-shell">
      <aside class="thread-list">
        <div class="thread-list-head">
          <span>对话</span>
          <el-tag size="small" effect="plain">{{ threads.length }}</el-tag>
        </div>

        <button
          v-for="item in threads"
          :key="item.id"
          type="button"
          class="thread-item"
          :class="{ active: isActive(item), unread: isUnread(item) }"
          @click="openThread(item)"
        >
          <el-avatar :size="40" class="thread-avatar" :src="item.knowledgeCover || undefined">
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

        <el-empty v-if="!threads.length" description="暂无邀请消息" />
      </aside>

      <section class="conversation">
        <template v-if="activeInvitation">
          <div class="conversation-head">
            <el-avatar :size="44" class="conversation-avatar" :src="activeInvitation.knowledgeCover || undefined">
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
                <span v-if="activeInvitation.readTime">已读</span>
                <span v-else>未读</span>
              </div>
            </div>
            <el-button
              v-if="activeInvitation.status === 'ACCEPTED'"
              :icon="Document"
              @click="goKnowledge(activeInvitation.knowledgeId)"
            >
              打开知识库
            </el-button>
          </div>

          <div class="chat-stream">
            <div class="bubble-row" :class="activeInvitation.direction">
              <el-avatar
                :size="36"
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
                    接受
                  </el-button>
                  <el-button :icon="Close" :loading="actioning" @click="rejectActive">
                    拒绝
                  </el-button>
                </div>
              </div>
            </div>

            <div class="system-note">
              {{ systemNote(activeInvitation) }}
            </div>
          </div>
        </template>

        <el-empty v-else description="选择一条邀请查看详情" />
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
    // 请求错误已由全局拦截器提示，这里保持页面可继续使用
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
    return `${peerName(item)} 发来邀请`
  }
  return '我发出的邀请'
}

const systemNote = (item) => {
  if (!item) return ''
  if (item.status === 'PENDING') return '等待对方处理'
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
  min-height: calc(100vh - 40px);
}
.page-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}
.page-head h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  color: #1f2937;
}
.page-head p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}
.empty-panel {
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}
.message-shell {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}
.thread-list,
.conversation {
  min-height: 0;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
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
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-weight: 600;
  color: #111827;
}
.thread-item {
  display: flex;
  width: 100%;
  padding: 14px 16px;
  gap: 12px;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.thread-item:hover {
  background: #f8fafc;
}
.thread-item.active {
  background: #eef5ff;
}
.thread-item.unread .thread-name {
  color: #2563eb;
}
.thread-avatar {
  flex: 0 0 auto;
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
  font-weight: 600;
  color: #111827;
  min-width: 0;
}
.thread-peer,
.thread-time,
.thread-snippet {
  font-size: 12px;
  color: #6b7280;
}
.thread-snippet {
  margin-top: 6px;
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
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
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
  font-weight: 600;
  color: #111827;
}
.conversation-subtitle {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
}
.chat-stream {
  flex: 1;
  min-height: 0;
  padding: 20px 18px 24px;
  overflow: auto;
  background: linear-gradient(180deg, #f8fafc 0%, #fff 100%);
}
.bubble-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 16px;
}
.bubble-row.outgoing {
  flex-direction: row-reverse;
}
.bubble-stack {
  max-width: min(72%, 620px);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bubble {
  padding: 14px 16px;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.bubble-row.incoming .bubble {
  border-top-left-radius: 2px;
}
.bubble-row.outgoing .bubble {
  border-top-right-radius: 2px;
  background: #eff6ff;
}
.bubble-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
}
.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
  color: #1f2937;
  line-height: 1.7;
}
.bubble-footer,
.bubble-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.bubble-footer {
  font-size: 12px;
  color: #6b7280;
}
.bubble-actions {
  justify-content: flex-end;
}
.system-note {
  padding: 6px 8px;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}
.conversation .el-empty {
  flex: 1;
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
