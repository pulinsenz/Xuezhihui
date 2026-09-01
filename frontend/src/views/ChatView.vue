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
              <template v-if="msg.role === 'assistant'">
                <!-- 回答属性标识：闲聊 / 哪个知识库 / 业务数据 / 通用回答 -->
                <div v-if="provenanceOf(msg).label" class="msg-provenance">
                  <el-tag size="small" :type="provenanceOf(msg).type" effect="plain">
                    {{ provenanceOf(msg).label }}
                  </el-tag>
                </div>
                <span v-if="msg.streaming" class="typing-cursor" />
              </template>
              <span class="msg-text" v-html="renderText(msg.content)" />

              <!-- 思考过程（可折叠） -->
              <details v-if="msg.role === 'assistant' && msg.thinking?.length" class="msg-thinking">
                <summary>🧠 思考过程</summary>
                <ul class="thinking-list">
                  <li v-for="(t, i) in msg.thinking" :key="i">{{ t }}</li>
                </ul>
              </details>

              <!-- 参考文献（使用了知识库才显示，可折叠；默认按设置折叠） -->
              <div v-if="msg.role === 'assistant' && msg.sources?.length && !msg.streaming" class="msg-refs">
                <div class="refs-title" @click="toggleRefs(msg)">
                  📚 参考文献（{{ msg.sources.length }}）
                  <span class="refs-arrow">{{ refsOpenOf(msg) ? '▾' : '▸' }}</span>
                </div>
                <ol v-if="refsOpenOf(msg)" class="refs-list">
                  <li v-for="(s, i) in msg.sources" :key="i">
                    <span class="refs-num">[{{ i + 1 }}]</span>
                    <span v-if="s.doc_name" class="refs-doc">📄 {{ s.doc_name }}</span>
                    <span class="refs-text">{{ truncateText(s.text) }}</span>
                  </li>
                </ol>
              </div>
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
import { listMyKnowledge, listDocs } from '../api/knowledge'
import { getSettings } from '../api/settings'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { useChatStore } from '../stores/chat'
import { parseStreamError } from '../utils/streamError'

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

// 回答属性：路由 → 标签文案与颜色（闲聊/知识库/业务数据/通用回答）
const provenanceOf = (msg) => {
  const route = msg.route
  if (route === 'kb') {
    const kb = kbList.value.find((k) => String(k.id) === String(msg.knowledge_id))
    return { label: kb ? `📚 知识库「${kb.name}」` : '📚 知识库', type: 'primary' }
  }
  if (route === 'chitchat') return { label: '💬 闲聊', type: 'info' }
  if (route === 'business') return { label: '📊 业务数据', type: 'success' }
  if (route === 'other') return { label: '🤖 通用回答', type: 'warning' }
  return { label: '', type: 'info' }
}

// 参考文献片段截断展示
const truncateText = (text, max = 120) => {
  text = (text || '').replace(/\s+/g, ' ').trim()
  return text.length > max ? `${text.slice(0, max)}…` : text
}

// 来源文档名：按知识库缓存 { knowledge_id: { doc_id: 文件名 } }，供参考文献标注来源文档
const docNameMaps = ref({})

const ensureDocNames = async (knowledgeId) => {
  if (!knowledgeId || docNameMaps.value[knowledgeId]) return docNameMaps.value[knowledgeId]
  try {
    const docs = await listDocs(knowledgeId, 0)
    const map = {}
    for (const d of docs) map[d.id] = d.name
    docNameMaps.value[knowledgeId] = map
    return map
  } catch (e) {
    return {}
  }
}

// 为消息的 sources 补上来源文档名（done 后调用）
const enrichSourceNames = async (msg) => {
  if (!msg?.sources?.length || !msg.knowledge_id) return
  const map = await ensureDocNames(msg.knowledge_id)
  for (const s of msg.sources) {
    if (s.doc_id && map[s.doc_id]) s.doc_name = map[s.doc_id]
  }
}

// 参考文献展开状态：消息自带 refsOpen 则用之，否则取设置默认（折叠=关）
const refsOpenOf = (msg) => (msg.refsOpen !== undefined ? msg.refsOpen : !refsCollapsedDefault.value)

const toggleRefs = (msg) => {
  msg.refsOpen = !refsOpenOf(msg)
}

// 历史消息加载后补充来源文档名（重载历史时还原参考文献的来源文档）
const enrichHistorySources = async () => {
  await Promise.all(
    chat.messages
      .filter((m) => m.sources?.length && m.knowledge_id)
      .map((m) => enrichSourceNames(m))
  )
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

    // 非 SSE 响应：Java 业务异常（未登录/登录失效/无权会话/限流等）返回 HTTP 200 + application/json。
    // SSE 解析器找不到 data: 事件会静默落成「（无回答）」，这里显式解析并抛真实错误。
    if ((resp.headers.get('content-type') || '').includes('application/json')) {
      const { message: msg, code } = parseStreamError(await resp.text())
      // 登录失效：raw fetch 绕过了 axios 拦截器，需手动清登录态并派发 auth:expired
      if (code === 40100) {
        authStore.clear()
        window.dispatchEvent(new Event('auth:expired'))
      }
      throw new Error(msg)
    }

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
    // 未收到 done 事件时兜底收尾
    if (!finished) await chat.finishStream({})
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

// ---- 初始化与路由同步 ----
const initLoggedIn = async () => {
  await chat.loadSessions()
  // 加载用户设置：参考文献默认折叠
  try {
    const settings = await getSettings()
    refsCollapsedDefault.value = (settings.collapseRefs ?? 1) === 1
  } catch (e) {
    // 错误已由拦截器提示，保持默认折叠
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
/* 回答属性标识 */
.msg-provenance {
  margin-bottom: 6px;
}
/* 思考过程 */
.msg-thinking {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 6px;
}
.msg-thinking summary {
  cursor: pointer;
  user-select: none;
}
.thinking-list {
  margin: 6px 0 0;
  padding-left: 16px;
  line-height: 1.8;
}
/* 参考文献 */
.msg-refs {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
  font-size: 12px;
}
.refs-title {
  color: #606266;
  font-weight: 600;
  margin-bottom: 4px;
  cursor: pointer;
  user-select: none;
}
.refs-arrow {
  font-size: 11px;
  color: #909399;
}
.refs-list {
  margin: 0;
  padding-left: 20px;
  color: #909399;
  line-height: 1.8;
}
.refs-num {
  color: #409eff;
  font-weight: 600;
}
.refs-doc {
  color: #606266;
  font-weight: 600;
  margin-right: 6px;
}
.msg-bubble.user .refs-title,
.msg-bubble.user .refs-num,
.msg-bubble.user .refs-doc {
  color: #e0f0ff;
}
.msg-bubble.user .refs-list {
  color: #d9ecff;
}
.msg-bubble.user .msg-thinking {
  color: #bcd6f0;
  border-color: rgba(255, 255, 255, 0.25);
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
