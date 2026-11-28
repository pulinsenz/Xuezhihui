<template>
  <div class="messages-page">
    <header class="page-head">
      <h2 class="page-title">协作消息与邀请</h2>
      <el-button
        class="secondary-action"
        :icon="Refresh"
        :loading="messageStore.loading"
        :disabled="!!pendingAction"
        @click="reload"
      >
        刷新消息
      </el-button>
    </header>

    <section class="invitation-list" aria-labelledby="invitation-list-title">
      <header class="list-head">
        <div class="list-heading">
          <h3 id="invitation-list-title">协作邀请列表</h3>
          <span class="count-pill">{{ invitations.length }}</span>
        </div>
        <span v-if="messageStore.unreadCount > 0" class="unread-pill">{{ messageStore.unreadCount }} 未读</span>
      </header>

      <ul v-if="invitations.length" class="invitation-rows">
        <li v-for="item in invitations" :key="item.id" class="invitation-row">
          <div class="invitation-avatars" aria-hidden="true">
            <el-avatar class="inviter-avatar" :src="item.inviterAvatar || undefined">
              {{ avatarText(inviterName(item)) }}
            </el-avatar>
            <span>→</span>
            <el-avatar class="knowledge-avatar" :src="item.knowledgeCover || undefined">
              {{ avatarText(item.knowledgeName) }}
            </el-avatar>
          </div>

          <div class="invitation-copy">
            <div class="invitation-heading">
              <strong>{{ item.knowledgeName || '未命名知识库' }}</strong>
              <span class="invitation-status" :data-status="item.status">
                {{ statusLabels[item.status] || '未知' }}
              </span>
              <span v-if="isUnread(item)" class="unread-pill">未读</span>
            </div>
            <p>{{ invitationMessage(item) }}</p>
            <div class="invitation-times">
              <span>发起 {{ formatTime(item.createTime) }}</span>
              <span v-if="item.handleTime">处理 {{ formatTime(item.handleTime) }}</span>
            </div>
          </div>

          <div class="invitation-actions">
            <el-button
              v-if="item.status === 'ACCEPTED' && item.knowledgeId"
              :tag="RouterLink"
              :to="knowledgeRoute(item.knowledgeId)"
              :icon="Document"
              class="secondary-action"
            >
              打开知识库
            </el-button>

            <template v-if="item.direction === 'incoming' && item.status === 'PENDING'">
              <el-button
                type="primary"
                class="accept-action"
                :loading="isActing(item, 'accept')"
                :disabled="!!pendingAction"
                @click="acceptInvitation(item)"
              >
                接受邀请
              </el-button>
              <el-button
                class="secondary-action"
                :loading="isActing(item, 'reject')"
                :disabled="!!pendingAction"
                @click="rejectInvitation(item)"
              >
                拒绝邀请
              </el-button>
            </template>

            <el-button
              v-if="item.direction === 'outgoing' && item.status === 'REJECTED' && item.knowledgeId && item.targetUserId"
              class="secondary-action"
              :loading="isActing(item, 'resend')"
              :disabled="!!pendingAction"
              @click="resendInvitation(item)"
            >
              再次邀请
            </el-button>

            <el-button
              v-if="isUnread(item)"
              text
              class="read-action"
              :loading="isActing(item, 'read')"
              :disabled="!!pendingAction"
              @click="markRead(item)"
            >
              标为已读
            </el-button>
          </div>
        </li>
      </ul>

      <el-empty v-else-if="!messageStore.loading" description="暂无协作邀请通知" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Document, Refresh } from '@element-plus/icons-vue'
import { addMember } from '../api/knowledge'
import { useAuthStore } from '../stores/auth'
import { useMessageStore } from '../stores/messages'

const authStore = useAuthStore()
const messageStore = useMessageStore()
const router = useRouter()

const invitations = computed(() => messageStore.invitations)
const pendingAction = ref(null)
const statusLabels = { PENDING: '待处理', ACCEPTED: '已接受', REJECTED: '已拒绝' }
const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

const knowledgeRoute = (id) => ({ name: 'KnowledgeDetail', params: { id } })

const reload = async () => {
  try {
    await messageStore.loadInvitations()
  } catch {
    // 请求错误已由全局拦截器提示
  }
}

const runAction = async (item, action, run) => {
  if (pendingAction.value) return
  pendingAction.value = { id: item.id, action }
  try {
    await run()
  } catch {
    // 请求错误已由全局拦截器提示
  } finally {
    pendingAction.value = null
  }
}

const isActing = (item, action) => pendingAction.value?.id === item.id && pendingAction.value.action === action

const acceptInvitation = (item) => runAction(item, 'accept', async () => {
  const knowledgeId = await messageStore.accept(item.id)
  ElMessage.success('已接受邀请')
  if (knowledgeId) await router.push(knowledgeRoute(knowledgeId))
})

const rejectInvitation = (item) => runAction(item, 'reject', async () => {
  await messageStore.reject(item.id)
  ElMessage.success('已拒绝邀请')
})

const resendInvitation = (item) => runAction(item, 'resend', async () => {
  await addMember(item.knowledgeId, { userId: item.targetUserId })
  ElMessage.success('已重新发送邀请')
  await reload()
})

const markRead = (item) => runAction(item, 'read', () => messageStore.markRead(item.id))
const isUnread = (item) => item.direction === 'incoming' && !item.readTime
const avatarText = (text) => (text || '?').charAt(0).toUpperCase()

const inviterName = (item) => item.inviterName || (item.direction === 'outgoing' ? '我' : '对方')

const invitationMessage = (item) => {
  const knowledgeName = item.knowledgeName || '未命名知识库'
  if (item.direction === 'outgoing') {
    return `${inviterName(item)}邀请${item.targetUserName || '对方'}协作《${knowledgeName}》。`
  }
  return item.message || `${inviterName(item)}邀请你协作《${knowledgeName}》。`
}

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : timeFormatter.format(date)
}

onMounted(() => {
  if (authStore.isLogin) reload()
})
</script>

<style scoped>
.messages-page {
  display: grid;
  gap: 16px;
  width: 100%;
  font-size: var(--text-body);
}

.page-head,
.list-head,
.list-heading,
.invitation-avatars,
.invitation-heading,
.invitation-times,
.invitation-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-head,
.list-head {
  justify-content: space-between;
  flex-wrap: wrap;
}

.page-title {
  font-size: var(--text-heading);
  color: var(--t1);
}

.invitation-list {
  padding: 16px;
  background: var(--card);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
}

.list-head {
  margin-block-end: 12px;
}

.list-heading h3 {
  font-size: var(--text-body);
}

.count-pill,
.invitation-status,
.unread-pill {
  padding: 1px 8px;
  border: 1px solid var(--line);
  border-radius: var(--r3);
  font-size: var(--text-muted);
  line-height: 1.5;
  white-space: nowrap;
}

.count-pill,
.unread-pill,
.inviter-avatar {
  background: var(--tint);
  color: var(--brand);
}

.invitation-rows {
  display: grid;
  gap: 8px;
  list-style: none;
}

.invitation-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--r2);
}

.invitation-avatars .el-avatar {
  --el-avatar-size: 36px;
  flex-shrink: 0;
  font-weight: 700;
}

.knowledge-avatar {
  background: var(--brand);
  color: var(--card);
}

.invitation-heading,
.invitation-times,
.invitation-actions {
  flex-wrap: wrap;
}

.invitation-heading strong,
.invitation-copy p,
.invitation-times {
  overflow-wrap: anywhere;
}

.invitation-status[data-status='ACCEPTED'] {
  color: color-mix(in srgb, var(--el-color-success) 70%, var(--t1));
}

.invitation-status[data-status='PENDING'] {
  color: color-mix(in srgb, var(--el-color-warning) 60%, var(--t1));
}

.invitation-status[data-status='REJECTED'] {
  color: color-mix(in srgb, var(--el-color-danger) 70%, var(--t1));
}

.invitation-copy p {
  margin-block: 4px;
  color: var(--t3);
  line-height: 1.5;
  white-space: pre-wrap;
}

.invitation-avatars > span:not(.el-avatar),
.invitation-times {
  color: var(--t3);
}

.invitation-times {
  font-size: var(--text-muted);
}

.invitation-actions {
  justify-content: flex-end;
  max-width: 300px;
}

.invitation-actions .el-button + .el-button {
  margin-inline-start: 0;
}

.secondary-action,
.accept-action,
.read-action {
  min-height: 40px;
  border-radius: var(--r3);
}

.secondary-action {
  --el-button-bg-color: var(--card);
  --el-button-text-color: var(--t1);
  --el-button-border-color: var(--line);
  --el-button-hover-bg-color: var(--tint);
  --el-button-hover-border-color: var(--tint-strong);
  --el-button-hover-text-color: var(--brand);
  --el-button-active-bg-color: var(--tint-strong);
}

.accept-action {
  --el-button-bg-color: var(--brand);
  --el-button-hover-bg-color: var(--brand-hover);
  --el-button-hover-border-color: var(--brand-hover);
  --el-button-active-bg-color: var(--brand-pressed);
  --el-button-active-border-color: var(--brand-pressed);
}

.invitation-actions .el-button:focus-visible,
.page-head .el-button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

@container (max-width: 680px) {
  .invitation-list {
    padding: 12px;
  }

  .invitation-row {
    grid-template-columns: auto minmax(0, 1fr);
    gap: 8px;
  }

  .invitation-avatars {
    gap: 4px;
  }

  .invitation-avatars .el-avatar {
    --el-avatar-size: 28px;
  }

  .invitation-actions {
    grid-column: 2;
    justify-content: flex-start;
    max-width: none;
  }

  .invitation-actions:empty {
    display: none;
  }
}
</style>
