<template>
  <div class="chat-page">
    <!-- 顶部：标题 + 知识库选择 -->
    <div class="chat-header">
      <h2>AI 对话</h2>
      <el-select
        v-model="knowledgeId"
        placeholder="选择知识库（可选，不选则无法基于资料回答）"
        clearable
        filterable
        class="kb-select"
        :loading="kbLoading"
      >
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
    </div>

    <div class="chat-body">
      <!-- 左侧：会话历史 -->
      <aside class="session-panel">
        <el-button type="primary" class="new-chat-btn" :icon="Plus" :disabled="chat.sending" @click="handleNewSession">
          新对话
        </el-button>

        <template v-if="authStore.isLogin">
          <div v-if="chat.sessions.length" class="session-list">
            <div
              v-for="s in chat.sessions"
              :key="s.session_id"
              class="session-item"
              :class="{ active: s.session_id === chat.activeSessionId }"
              @click="handleOpenSession(s)"
            >
              <div class="session-title">{{ s.title }}</div>
              <div class="session-sub">{{ formatTime(s.update_time) }} · {{ s.message_count }} 条</div>
              <el-icon v-if="!chat.sending" class="session-del" title="删除会话" @click.stop="handleDeleteSession(s)">
                <Delete />
              </el-icon>
            </div>
          </div>
          <div v-else class="panel-tip">暂无会话，开始新对话吧</div>
        </template>
        <div v-else class="panel-tip login" @click="uiStore.openLogin()">登录后查看会话记录</div>
      </aside>

      <!-- 右侧：对话区 -->
      <div class="chat-column">
        <!-- 消息区 -->
        <div ref="messageAreaRef" class="message-area">
          <el-empty v-if="chat.messages.length === 0" description="你好，我是学智汇 AI 助手，有问题尽管问～" />

          <div v-for="(msg, idx) in chat.messages" :key="idx" class="msg-row" :class="msg.role">
            <el-avatar :size="34" class="msg-avatar" :class="msg.role">
              {{ msg.role === 'user' ? '我' : 'AI' }}
            </el-avatar>
            <div class="msg-bubble" :class="msg.role">
              <span v-if="msg.role === 'assistant' && msg.streaming" class="typing-cursor" />
              <span class="msg-text" v-html="renderText(msg.content)" />
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
            :disabled="chat.sending"
            @keydown.enter.exact.prevent="handleSend"
          />
          <el-button type="primary" :icon="Promotion" :loading="chat.sending" @click="handleSend">
            {{ chat.sending ? '回答中' : '发送' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Promotion } from '@element-plus/icons-vue'
import { listMyKnowledge } from '../api/knowledge'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { useChatStore } from '../stores/chat'

const authStore = useAuthStore()
const uiStore = useUiStore()
const chat = useChatStore()
const route = useRoute()
const router = useRouter()

const inputText = ref('')
const knowledgeId = ref('')
const kbList = ref([])
const kbLoading = ref(false)
const messageAreaRef = ref(null)

const scrollToBottom = () => {
  nextTick(() => {
    const el = messageAreaRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// 简单文本渲染：换行转 <br>，并高亮引用编号 [1][2]
const renderText = (text) => {
  const escaped = (text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return escaped
    .replace(/\n/g, '<br>')
    .replace(/\[(\d+)\]/g, '<span class="cite">[$1]</span>')
}

const loadKnowledge = async () => {
  // 未登录时知识库接口会被拦截（401），且访客无权查看个人知识库，跳过加载
  if (!authStore.isLogin) return
  kbLoading.value = true
  try {
    kbList.value = await listMyKnowledge()
  } finally {
    kbLoading.value = false
  }
}

const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text || chat.sending) return

  // 未登录 → 弹出登录弹窗，不发起对话请求
  if (!authStore.isLogin) {
    uiStore.openLogin()
    return
  }
  inputText.value = ''

  chat.pushUserMessage(text)
  chat.pushAssistantPlaceholder()
  chat.sending = true
  scrollToBottom()

  // 构建 SSE URL（token 走 header，不放进 URL，避免泄露）
  const params = new URLSearchParams({ session_id: chat.activeSessionId, query: text })
  if (knowledgeId.value) params.set('knowledge_id', knowledgeId.value)

  let finished = false
  try {
    const resp = await fetch(`/api/chat/stream?${params.toString()}`, {
      headers: { Authorization: `Bearer ${authStore.token}` },
    })
    if (!resp.ok || !resp.body) throw new Error(`对话请求失败: ${resp.status}`)

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // SSE 事件以空行分隔，逐事件解析
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const event = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const dataLine = event.split('\n').find((l) => l.startsWith('data:'))
        if (!dataLine) continue
        // 兼容 Spring SseEmitter 输出 `data:{json}` 与 Python 输出 `data: {json}`
        const payload = JSON.parse(dataLine.slice(5).trim())
        if (payload.type === 'token') {
          chat.appendToken(payload.content)
          scrollToBottom()
        } else if (payload.type === 'done') {
          finished = true
          await chat.finishStream(payload.answer || '')
          scrollToBottom()
        } else if (payload.type === 'error') {
          throw new Error(payload.message || '对话出错')
        }
      }
    }
    // 未收到 done 事件时兜底收尾
    if (!finished) await chat.finishStream('')
  } catch (e) {
    chat.failStream(e.message || '对话失败，请稍后再试')
    ElMessage.error(e.message || '对话失败，请稍后再试')
  } finally {
    chat.sending = false
    scrollToBottom()
  }
}

// ---- 会话管理 ----
const handleNewSession = () => {
  if (chat.sending) return
  chat.newSession()
}

const handleOpenSession = (s) => {
  if (chat.sending) return
  chat.openSession(s.session_id)
}

const handleDeleteSession = async (s) => {
  if (chat.sending) return
  await ElMessageBox.confirm(`确定删除会话「${s.title}」吗？历史将不可恢复。`, '删除确认', { type: 'warning' })
  await chat.deleteSession(s.session_id)
}

const formatTime = (epochSec) => {
  if (!epochSec) return ''
  const d = new Date(Number(epochSec) * 1000)
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

// ---- 初始化与路由同步 ----
const initLoggedIn = async () => {
  await chat.loadSessions()
  const id = route.params.sessionId || localStorage.getItem('chat_session_id')
  if (id && chat.sessions.some((s) => s.session_id === id)) {
    await chat.openSession(id)
  } else {
    chat.newSession()
  }
}

onMounted(() => {
  loadKnowledge()
  scrollToBottom()
  if (authStore.isLogin) initLoggedIn()
  else chat.resetForGuest()
})

// 登录态变化：登录则加载历史，退出则重置为访客
watch(
  () => authStore.isLogin,
  (login) => {
    if (login) {
      initLoggedIn()
      loadKnowledge()
    } else {
      chat.resetForGuest()
    }
  }
)

// 会话切换后同步 URL → /chat/{sessionId}
watch(
  () => chat.activeSessionId,
  (id) => {
    if (id && route.params.sessionId !== id) {
      router.replace({ name: 'Chat', params: { sessionId: id } })
    }
  }
)

// 反向：URL 变化（粘贴他人/未知会话链接）→ 属主会话则打开，否则新建
watch(
  () => route.params.sessionId,
  async (id) => {
    if (!id || id === chat.activeSessionId || chat.sending) return
    const known = chat.sessions.some((s) => s.session_id === id)
    if (known) await chat.openSession(id)
    else chat.newSession()
  }
)
</script>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.chat-header h2 {
  font-size: 18px;
}
.kb-select {
  width: 280px;
}
.chat-body {
  flex: 1;
  display: flex;
  min-height: 0;
}
/* 会话面板 */
.session-panel {
  width: 250px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  background: #fafbfc;
}
.new-chat-btn {
  width: 100%;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.session-item {
  position: relative;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
}
.session-item:hover {
  background: #f0f2f5;
}
.session-item.active {
  background: #ecf5ff;
}
.session-title {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 20px;
}
.session-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.session-del {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  color: #c0c4cc;
  font-size: 14px;
}
.session-del:hover {
  color: #f56c6c;
}
.panel-tip {
  padding: 20px 8px;
  font-size: 13px;
  color: #909399;
  text-align: center;
}
.panel-tip.login {
  cursor: pointer;
}
.panel-tip.login:hover {
  color: #409eff;
}
/* 对话列 */
.chat-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 0 20px;
}
.message-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px 4px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.msg-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.msg-row.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  flex-shrink: 0;
}
.msg-avatar.user {
  background: #409eff;
  color: #fff;
}
.msg-avatar.assistant {
  background: #67c23a;
  color: #fff;
}
.msg-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.msg-bubble.user {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}
.msg-bubble.assistant {
  background: #f0f2f5;
  color: #303133;
  border-top-left-radius: 2px;
}
.cite {
  color: #409eff;
  font-weight: 600;
}
.msg-bubble.user .cite {
  color: #e0f0ff;
}
.typing-cursor {
  display: inline-block;
  width: 8px;
  height: 16px;
  margin-right: 2px;
  vertical-align: text-bottom;
  background: #67c23a;
  animation: blink 0.8s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.chat-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 14px 0 18px;
  border-top: 1px solid var(--el-border-color-light);
}
.chat-input :deep(.el-textarea__inner) {
  font-size: 14px;
}
</style>
