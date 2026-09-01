<template>
  <div class="chat-page">
    <!-- 顶部：标题 + 知识库选择与状态 -->
    <div class="chat-header">
      <div class="header-main">
        <div class="header-badge">AI Assistant</div>
        <h2>智能对话</h2>
      </div>
      <div class="header-right">
        <el-select
          v-model="knowledgeId"
          placeholder="选择关联知识库（可选）"
          clearable
          filterable
          class="kb-select"
          :loading="kbLoading"
        >
          <template #prefix>
            <el-icon class="select-icon"><Folder /></el-icon>
          </template>
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id">
            <div class="kb-option-row">
              <span class="kb-option-name">{{ kb.name }}</span>
              <el-tag size="small" effect="plain" type="info">{{ kb.docCount || 0 }} 篇文档</el-tag>
            </div>
          </el-option>
        </el-select>
      </div>
    </div>

    <div class="chat-body">
      <!-- 左侧：会话历史侧栏 -->
      <aside class="session-panel">
        <el-button
          type="primary"
          class="new-chat-btn"
          :icon="Plus"
          :disabled="chat.sending"
          @click="handleNewSession"
        >
          开启新对话
        </el-button>

        <template v-if="authStore.isLogin">
          <div class="session-section-title">
            <span>最近会话</span>
            <el-tag size="small" round effect="plain">{{ chat.sessions.length }}</el-tag>
          </div>
          <div v-if="chat.sessions.length" class="session-list">
            <div
              v-for="s in chat.sessions"
              :key="s.session_id"
              class="session-item"
              :class="{ active: s.session_id === chat.activeSessionId }"
              @click="handleOpenSession(s)"
            >
              <el-icon class="session-lead-icon"><ChatLineSquare /></el-icon>
              <div class="session-info">
                <div class="session-title" :title="s.title">{{ s.title || '未命名会话' }}</div>
                <div class="session-sub">{{ formatTime(s.update_time) }} · {{ s.message_count }} 条消息</div>
              </div>
              <el-icon
                v-if="!chat.sending"
                class="session-del"
                title="删除会话"
                @click.stop="handleDeleteSession(s)"
              >
                <Delete />
              </el-icon>
            </div>
          </div>
          <div v-else class="panel-tip">暂无历史会话，点击上方开启新对话</div>
        </template>
        <div v-else class="panel-tip login" @click="uiStore.openLogin()">
          <el-icon><User /></el-icon>
          <span>登录后同步与查看会话历史</span>
        </div>
      </aside>

      <!-- 右侧：对话区 -->
      <div class="chat-column">
        <!-- 消息流展示区 -->
        <div ref="messageAreaRef" class="message-area">
          <div v-if="chat.messages.length === 0" class="chat-welcome">
            <div class="welcome-icon-box">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <h3>你好，我是学智汇 AI 助手</h3>
            <p>基于检索增强生成技术，支持挂载知识库回答专业问题或自由交互。</p>
            <div class="welcome-suggestions">
              <button
                v-for="tip in suggestions"
                :key="tip"
                class="suggest-chip"
                @click="useSuggestion(tip)"
              >
                {{ tip }}
              </button>
            </div>
          </div>

          <div
            v-for="(msg, idx) in chat.messages"
            :key="idx"
            class="msg-row"
            :class="msg.role"
          >
            <el-avatar :size="36" class="msg-avatar" :class="msg.role">
              <template v-if="msg.role === 'user'">
                {{ userAvatarText }}
              </template>
              <template v-else>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-avatar>

            <div class="msg-bubble-wrap" :class="msg.role">
              <div class="msg-bubble" :class="msg.role">
                <template v-if="msg.role === 'assistant'">
                  <!-- 意图与数据来源标识 -->
                  <div v-if="provenanceOf(msg).label" class="msg-provenance">
                    <el-tag size="small" :type="provenanceOf(msg).type" effect="plain" class="route-tag">
                      {{ provenanceOf(msg).label }}
                    </el-tag>
                  </div>
                  <span v-if="msg.streaming" class="typing-cursor" />
                </template>

                <div class="msg-text" v-html="renderText(msg.content)" />

                <!-- 思考过程折叠卡片 -->
                <details v-if="msg.role === 'assistant' && msg.thinking?.length" class="msg-thinking">
                  <summary class="thinking-summary">
                    <span class="thinking-label">
                      <el-icon><Opportunity /></el-icon>
                      思考过程 ({{ msg.thinking.length }} 步)
                    </span>
                  </summary>
                  <ul class="thinking-list">
                    <li v-for="(t, i) in msg.thinking" :key="i">{{ t }}</li>
                  </ul>
                </details>

                <!-- 参考文献（知识库溯源引用） -->
                <div
                  v-if="msg.role === 'assistant' && msg.sources?.length && !msg.streaming"
                  class="msg-refs"
                >
                  <div class="refs-title" @click="toggleRefs(msg)">
                    <div class="refs-title-left">
                      <el-icon><Document /></el-icon>
                      <span>引用知识源 ({{ msg.sources.length }})</span>
                    </div>
                    <span class="refs-arrow">{{ refsOpenOf(msg) ? '收起 ▴' : '展开 ▾' }}</span>
                  </div>
                  <div v-if="refsOpenOf(msg)" class="refs-list-box">
                    <div v-for="(s, i) in msg.sources" :key="i" class="ref-item">
                      <div class="ref-lead">
                        <span class="refs-num">[{{ i + 1 }}]</span>
                        <span v-if="s.doc_name" class="refs-doc">{{ s.doc_name }}</span>
                      </div>
                      <div class="refs-text">{{ truncateText(s.text) }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入控制区 -->
        <div class="chat-input-wrapper">
          <div class="input-card">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="输入你的问题，按 Enter 发送，Shift + Enter 换行..."
              :disabled="chat.sending"
              class="custom-textarea"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="input-toolbar">
              <div class="input-hints">
                <span>Enter 发送 / Shift+Enter 换行</span>
              </div>
              <el-button
                type="primary"
                class="send-btn"
                :loading="chat.sending"
                :disabled="!inputText.trim()"
                @click="handleSend"
              >
                <span>{{ chat.sending ? '生成中' : '发送' }}</span>
                <el-icon class="send-icon"><Promotion /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, nextTick, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  ChatLineSquare,
  Cpu,
  Delete,
  Document,
  Folder,
  Opportunity,
  Plus,
  Promotion,
  User,
} from '@element-plus/icons-vue'
import { listMyKnowledge, listDocs } from '../api/knowledge'
import { getSettings } from '../api/settings'
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
// 参考文献默认折叠（来自用户设置，1=折叠 0=展开）
const refsCollapsedDefault = ref(true)

const suggestions = [
  '请总结一下当前知识库的核心内容',
  '知识库里的文档有哪些关键要点？',
  '你能帮我解答关于学习与工作的常见疑问吗？',
]

const userAvatarText = computed(() => {
  const name = authStore.user?.userName || authStore.user?.userAccount || '我'
  return name.charAt(0).toUpperCase()
})

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

// 回答属性：路由 → 标签文案与颜色
const provenanceOf = (msg) => {
  const r = msg.route
  if (r === 'kb') {
    const kb = kbList.value.find((k) => String(k.id) === String(msg.knowledge_id))
    return { label: kb ? `📚 知识库「${kb.name}」` : '📚 知识库增强检索', type: 'primary' }
  }
  if (r === 'chitchat') return { label: '💬 智能闲聊', type: 'info' }
  if (r === 'business') return { label: '📊 业务知识检索', type: 'success' }
  if (r === 'other') return { label: '🤖 通用模型回答', type: 'warning' }
  return { label: '', type: 'info' }
}

// 参考文献片段截断展示
const truncateText = (text, max = 150) => {
  text = (text || '').replace(/\s+/g, ' ').trim()
  return text.length > max ? `${text.slice(0, max)}…` : text
}

// 来源文档名缓存
const docNameMaps = ref({})

const ensureDocNames = async (kId) => {
  if (!kId || docNameMaps.value[kId]) return docNameMaps.value[kId]
  try {
    const docs = await listDocs(kId, 0)
    const map = {}
    for (const d of docs) map[d.id] = d.name
    docNameMaps.value[kId] = map
    return map
  } catch (e) {
    return {}
  }
}

const enrichSourceNames = async (msg) => {
  if (!msg?.sources?.length || !msg.knowledge_id) return
  const map = await ensureDocNames(msg.knowledge_id)
  for (const s of msg.sources) {
    if (s.doc_id && map[s.doc_id]) s.doc_name = map[s.doc_id]
  }
}

const refsOpenOf = (msg) => (msg.refsOpen !== undefined ? msg.refsOpen : !refsCollapsedDefault.value)

const toggleRefs = (msg) => {
  msg.refsOpen = !refsOpenOf(msg)
}

const enrichHistorySources = async () => {
  await Promise.all(
    chat.messages
      .filter((m) => m.sources?.length && m.knowledge_id)
      .map((m) => enrichSourceNames(m))
  )
}

const loadKnowledge = async () => {
  if (!authStore.isLogin) return
  kbLoading.value = true
  try {
    kbList.value = await listMyKnowledge()
  } finally {
    kbLoading.value = false
  }
}

const useSuggestion = (tip) => {
  inputText.value = tip
}

const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text || chat.sending) return

  if (!authStore.isLogin) {
    uiStore.openLogin()
    return
  }
  inputText.value = ''

  chat.pushUserMessage(text)
  chat.pushAssistantPlaceholder()
  chat.sending = true
  scrollToBottom()

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
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const event = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const dataLine = event.split('\n').find((l) => l.startsWith('data:'))
        if (!dataLine) continue
        const payload = JSON.parse(dataLine.slice(5).trim())
        if (payload.type === 'token') {
          chat.appendToken(payload.content)
          scrollToBottom()
        } else if (payload.type === 'thinking') {
          chat.addThinking(payload.content || '')
        } else if (payload.type === 'done') {
          finished = true
          await chat.finishStream(payload)
          await enrichSourceNames(chat.messages[chat.messages.length - 1])
          scrollToBottom()
        } else if (payload.type === 'error') {
          throw new Error(payload.message || '对话出错')
        }
      }
    }
    if (!finished) await chat.finishStream({})
  } catch (e) {
    chat.failStream(e.message || '对话失败，请稍后再试')
    ElMessage.error(e.message || '对话失败，请稍后再试')
  } finally {
    chat.sending = false
    scrollToBottom()
  }
}

const handleNewSession = () => {
  if (chat.sending) return
  chat.newSession()
}

const handleOpenSession = async (s) => {
  if (chat.sending) return
  await chat.openSession(s.session_id)
  enrichHistorySources()
  scrollToBottom()
}

const handleDeleteSession = async (s) => {
  if (chat.sending) return
  await ElMessageBox.confirm(`确定删除会话「${s.title}」吗？历史将不可恢复。`, '删除确认', { type: 'warning' })
  await chat.deleteSession(s.session_id)
  enrichHistorySources()
  scrollToBottom()
}

const formatTime = (epochSec) => {
  if (!epochSec) return ''
  const d = new Date(Number(epochSec) * 1000)
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

const initLoggedIn = async () => {
  await chat.loadSessions()
  try {
    const settings = await getSettings()
    refsCollapsedDefault.value = (settings.collapseRefs ?? 1) === 1
  } catch (e) {
    // 默认折叠
  }
  const id = route.params.sessionId || localStorage.getItem('chat_session_id')
  if (id && chat.sessions.some((s) => s.session_id === id)) {
    await chat.openSession(id)
    enrichHistorySources()
    scrollToBottom()
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

watch(
  () => chat.activeSessionId,
  (id) => {
    if (id && route.params.sessionId !== id) {
      router.replace({ name: 'Chat', params: { sessionId: id } })
    }
  }
)

watch(
  () => route.params.sessionId,
  async (id) => {
    if (!id || id === chat.activeSessionId || chat.sending) return
    const known = chat.sessions.some((s) => s.session_id === id)
    if (known) {
      await chat.openSession(id)
      enrichHistorySources()
      scrollToBottom()
    } else {
      chat.newSession()
    }
  }
)
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 144px);
  min-height: 580px;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(16px);
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  background: #ffffff;
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
}

.header-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-badge {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
}

.header-main h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.kb-select {
  width: 320px;
}

.select-icon {
  color: #14b8a6;
}

.kb-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.kb-option-name {
  font-weight: 500;
}

.chat-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 左侧会话侧栏 */
.session-panel {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid rgba(226, 232, 240, 0.8);
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #f8fafc;
  overflow-y: auto;
}

.new-chat-btn {
  width: 100%;
  height: 40px;
  font-weight: 600;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(20, 184, 166, 0.2);
}

.session-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 0;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  cursor: pointer;
  background: transparent;
  transition: all 0.2s ease;
}

.session-item:hover {
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.session-item.active {
  background: #ffffff;
  border: 1px solid rgba(20, 184, 166, 0.25);
  box-shadow: 0 4px 12px rgba(20, 184, 166, 0.08);
}

.session-lead-icon {
  font-size: 16px;
  color: #94a3b8;
  flex-shrink: 0;
}

.session-item.active .session-lead-icon {
  color: #0f766e;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 18px;
}

.session-item.active .session-title {
  color: #0f766e;
}

.session-sub {
  margin-top: 3px;
  font-size: 11px;
  color: #94a3b8;
}

.session-del {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #cbd5e1;
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s ease, color 0.2s ease;
}

.session-item:hover .session-del {
  opacity: 1;
}

.session-del:hover {
  color: #ef4444;
}

.panel-tip {
  padding: 30px 12px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  line-height: 1.6;
}

.panel-tip.login {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  border: 1px dashed rgba(203, 213, 225, 0.8);
  border-radius: 12px;
  padding: 20px 12px;
  transition: all 0.2s ease;
}

.panel-tip.login:hover {
  color: #0f766e;
  border-color: #14b8a6;
  background: rgba(20, 184, 166, 0.04);
}

/* 右侧对话区 */
.chat-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #ffffff;
}

.message-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 欢迎空态 */
.chat-welcome {
  margin: auto;
  max-width: 540px;
  text-align: center;
  padding: 32px 20px;
}

.welcome-icon-box {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.15), rgba(14, 165, 233, 0.15));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  color: #0f766e;
}

.chat-welcome h3 {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 8px;
}

.chat-welcome p {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 24px;
}

.welcome-suggestions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.suggest-chip {
  padding: 10px 16px;
  background: #f8fafc;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  font-size: 13px;
  color: #334155;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.suggest-chip:hover {
  background: #f0fdfa;
  border-color: rgba(20, 184, 166, 0.4);
  color: #0f766e;
  transform: translateY(-1px);
}

/* 消息流气泡 */
.msg-row {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.msg-row.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.msg-avatar.user {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  font-weight: 600;
}

.msg-avatar.assistant {
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  color: #fff;
  font-size: 18px;
}

.msg-bubble-wrap {
  max-width: 78%;
  display: flex;
  flex-direction: column;
}

.msg-bubble-wrap.user {
  align-items: flex-end;
}

.msg-bubble {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.03);
}

.msg-bubble.user {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #ffffff;
  border-top-right-radius: 4px;
}

.msg-bubble.assistant {
  background: #f8fafc;
  color: #1e293b;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-top-left-radius: 4px;
}

.cite {
  display: inline-block;
  margin: 0 2px;
  padding: 0 4px;
  border-radius: 4px;
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
  font-weight: 600;
  font-size: 12px;
}

.msg-bubble.user .cite {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

.msg-provenance {
  margin-bottom: 8px;
}

.route-tag {
  font-weight: 500;
}

/* 思考过程 */
.msg-thinking {
  margin-top: 12px;
  padding: 10px 14px;
  background: #ffffff;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 12px;
  font-size: 12px;
}

.thinking-summary {
  cursor: pointer;
  user-select: none;
  font-weight: 600;
  color: #64748b;
}

.thinking-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.thinking-list {
  margin: 8px 0 0;
  padding-left: 18px;
  line-height: 1.8;
  color: #64748b;
}

/* 参考文献 */
.msg-refs {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed rgba(226, 232, 240, 0.9);
}

.refs-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #334155;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
}

.refs-title-left {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #0f766e;
}

.refs-arrow {
  font-size: 11px;
  color: #94a3b8;
}

.refs-list-box {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ref-item {
  padding: 10px 12px;
  background: #ffffff;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 10px;
  font-size: 12px;
}

.ref-lead {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.refs-num {
  font-weight: 700;
  color: #0f766e;
}

.refs-doc {
  font-weight: 600;
  color: #1e293b;
}

.refs-text {
  color: #64748b;
  line-height: 1.6;
}

.typing-cursor {
  display: inline-block;
  width: 6px;
  height: 16px;
  margin-right: 4px;
  vertical-align: text-bottom;
  background: #14b8a6;
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 输入框 */
.chat-input-wrapper {
  padding: 16px 28px 24px;
  background: #ffffff;
  border-top: 1px solid rgba(226, 232, 240, 0.8);
}

.input-card {
  border: 1px solid rgba(203, 213, 225, 0.8);
  border-radius: 16px;
  padding: 10px 14px;
  background: #ffffff;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.input-card:focus-within {
  border-color: #14b8a6;
  box-shadow: 0 4px 20px rgba(20, 184, 166, 0.12);
}

.custom-textarea :deep(.el-textarea__inner) {
  border: 0 !important;
  box-shadow: none !important;
  padding: 4px 0 !important;
  font-size: 14px;
  line-height: 1.6;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(241, 245, 249, 0.9);
}

.input-hints {
  font-size: 11px;
  color: #94a3b8;
}

.send-btn {
  border-radius: 10px;
  font-weight: 600;
  padding: 8px 18px;
}

.send-icon {
  margin-left: 4px;
}

@media (max-width: 768px) {
  .chat-header {
    flex-direction: column;
    align-items: flex-start;
    padding: 12px 16px;
  }
  .kb-select {
    width: 100%;
  }
  .session-panel {
    display: none;
  }
  .message-area {
    padding: 16px;
  }
  .chat-input-wrapper {
    padding: 12px 16px 16px;
  }
}
</style>