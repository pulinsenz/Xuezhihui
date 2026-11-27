<template>
  <div class="chat-page" @keydown.esc="showSessions = false">
    <!-- 顶部：标题 + 知识库选择与状态 -->
    <header class="chat-header">
      <div class="header-main">
        <div class="title-with-badge">
          <div class="header-icon-box">
            <el-icon><Cpu /></el-icon>
          </div>
          <div class="title-meta">
            <h2>智能对话</h2>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button
          class="session-toggle action-btn"
          :icon="ChatLineSquare"
          aria-controls="chat-session-panel"
          :aria-expanded="showSessions"
          @click="showSessions = !showSessions"
        >{{ showSessions ? '关闭会话' : '会话记录' }}</el-button>
        <el-dropdown :disabled="!chat.messages.length || chat.sending" @command="exportChat">
          <el-button class="action-btn" :disabled="!chat.messages.length || chat.sending">
            <span>导出对话</span>
            <el-icon class="btn-icon-right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="md">导出 Markdown (.md)</el-dropdown-item>
              <el-dropdown-item command="txt">导出纯文本 (.txt)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-select
          v-model="knowledgeId"
          aria-label="关联知识库"
          placeholder="选择关联知识库（可选）"
          clearable
          filterable
          class="kb-select"
          :loading="kbLoading"
          @change="onKnowledgeChange"
        >
          <template #prefix>
            <el-icon class="select-icon"><Folder /></el-icon>
          </template>
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="String(kb.id)">
            <div class="kb-option-row">
              <span class="kb-option-name">{{ kb.name }}</span>
              <el-tag size="small" effect="plain" type="info">{{ kb.docCount || 0 }} 篇文档</el-tag>
            </div>
          </el-option>
        </el-select>
      </div>
    </header>

    <div class="chat-body">
      <!-- 左侧：会话历史侧栏（弹性比例 clamp，抗大比例缩放挤压） -->
      <aside id="chat-session-panel" class="session-panel" :class="{ 'is-open': showSessions }" aria-label="历史会话">
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
          <div class="search-box-wrap">
            <el-input
              v-model="sessionSearch"
              clearable
              aria-label="搜索会话标题"
              placeholder="搜索会话记录..."
              :prefix-icon="Search"
              class="session-search-input"
            />
          </div>

          <div class="session-section-title">
            <span class="section-label">历史会话</span>
            <el-tag size="small" round effect="plain" class="count-tag">{{ chat.sessions.length }}</el-tag>
          </div>

          <div v-if="filteredSessions.length" class="session-list custom-scroll">
            <div
              v-for="s in filteredSessions"
              :key="s.session_id"
              class="session-item"
              :class="{ active: s.session_id === chat.activeSessionId }"
            >
              <button
                type="button"
                class="session-open"
                :disabled="chat.sending"
                @click="handleOpenSession(s)"
              >
                <div class="session-lead-badge" :class="{ 'is-pinned': s.pinned }">
                  <el-icon><ChatLineSquare /></el-icon>
                </div>
                <div class="session-info">
                  <span class="session-title" :title="s.title">{{ s.title || '未命名会话' }}</span>
                  <div class="session-sub">
                    <span v-if="s.pinned" class="pinned-tag">已置顶</span>
                    <span>{{ formatTime(s.update_time) }}</span>
                    <span class="dot-sep">·</span>
                    <span>{{ s.message_count }} 条消息</span>
                  </div>
                </div>
              </button>

              <div class="session-tools">
                <el-button
                  text
                  size="small"
                  :aria-pressed="!!s.pinned"
                  :loading="pinBusy === s.session_id"
                  :disabled="chat.sending || !!pinBusy"
                  @click="handlePin(s)"
                >
                  {{ s.pinned ? '取消置顶' : '置顶' }}
                </el-button>
                <el-button
                  text
                  size="small"
                  :disabled="chat.sending"
                  @click="handleRenameSession(s)"
                >
                  重命名
                </el-button>
                <el-button
                  text
                  size="small"
                  class="danger-btn"
                  :disabled="chat.sending"
                  @click="handleDeleteSession(s)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>
          <div v-else class="panel-tip">
            <el-icon class="tip-icon"><ChatDotRound /></el-icon>
            <p>{{ sessionSearch.trim() ? '没有匹配的会话，请尝试其他关键词' : '暂无历史会话，点击上方开启新对话' }}</p>
          </div>
        </template>
      </aside>

      <!-- 右侧：核心对话流与交互区 -->
      <section class="chat-column" aria-label="对话内容">
        <!-- 消息流展示区 -->
        <div ref="messageAreaRef" class="message-area custom-scroll" @scroll="onScroll" @click="onMessageAreaClick">
          <!-- 空态欢迎面板 -->
          <div v-if="chat.messages.length === 0" class="chat-welcome">
            <div class="welcome-icon-box">
              <el-icon><Cpu /></el-icon>
            </div>
            <h3>你好，我是学智汇学术 AI 助手</h3>
            <p class="welcome-desc">
              采用检索增强生成 (RAG) 融合架构。你可以直接关联知识库展开循证问答，或就通用学术问题自由探讨。
            </p>

            <div class="welcome-suggestions-grid">
              <div class="suggest-header">
                <el-icon><Opportunity /></el-icon>
                <span>推荐提问试一试</span>
              </div>
              <div class="suggest-items">
                <button
                  v-for="tip in suggestions"
                  :key="tip"
                  type="button"
                  class="suggest-chip"
                  @click="useSuggestion(tip)"
                >
                  <span class="chip-text">{{ tip }}</span>
                  <el-icon class="chip-arrow"><ArrowRight /></el-icon>
                </button>
              </div>
            </div>
          </div>

          <!-- 消息列表 -->
          <div
            v-for="(msg, idx) in chat.messages"
            :key="idx"
            class="msg-row"
            :class="[msg.role, { cont: isContMsg(idx) }]"
            :data-midx="idx"
          >
            <!-- 连续同人消息不重复头像 -->
            <el-avatar v-if="!isContMsg(idx)" :size="38" class="msg-avatar" :class="msg.role">
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
                  <div v-if="provenanceOf(msg).label && (msg.content || !msg.streaming)" class="msg-provenance">
                    <span class="provenance-pill" :class="provenanceOf(msg).type">
                      {{ provenanceOf(msg).label }}
                    </span>
                  </div>
                  <!-- 首个 token 到达前的思考中指示 -->
                  <div v-if="msg.streaming && !msg.content" class="thinking-indicator">
                    <span class="thinking-dots"><i /><i /><i /></span>
                    <span>正在检索切片并构建推理...</span>
                  </div>
                </template>

                <div v-if="msg.content" class="msg-text" v-html="bubbleHtmlOf(msg)" />

                <!-- 停止 / 失败尾注 -->
                <div v-if="msg.stopped" class="stopped-note">
                  <el-icon><VideoPause /></el-icon>
                  <span>已停止生成</span>
                </div>
                <div v-else-if="msg.errorText" class="failed-note">
                  <el-icon><WarningFilled /></el-icon>
                  <span>{{ msg.errorText }}</span>
                </div>

                <!-- 思考过程折叠卡片 -->
                <details v-if="msg.role === 'assistant' && msg.thinking?.length" class="msg-thinking">
                  <summary class="thinking-summary">
                    <span class="thinking-label">
                      <el-icon><Opportunity /></el-icon>
                      思考链与检索规划 ({{ msg.thinking.length }} 步)
                    </span>
                  </summary>
                  <ul class="thinking-list">
                    <li v-for="(t, i) in msg.thinking" :key="i">{{ t }}</li>
                  </ul>
                </details>

                <!-- 参考文献（知识库溯源引用卡片） -->
                <div
                  v-if="msg.role === 'assistant' && msg.sources?.length && !msg.streaming"
                  class="msg-refs"
                >
                  <button type="button" class="refs-title" :aria-expanded="refsOpenOf(msg)" @click="toggleRefs(msg)">
                    <span class="refs-title-left">
                      <el-icon><Document /></el-icon>
                      <span>引用知识源与参考片段 ({{ msg.sources.length }})</span>
                    </span>
                    <span class="refs-arrow">{{ refsOpenOf(msg) ? '收起 ▴' : '展开 ▾' }}</span>
                  </button>

                  <div v-if="refsOpenOf(msg)" class="refs-list-box">
                    <div
                      v-for="(s, i) in msg.sources"
                      :key="i"
                      class="ref-item"
                      :data-ref-no="String(s.cite_no ?? i + 1)"
                    >
                      <div class="ref-lead">
                        <span class="refs-num">[{{ s.cite_no ?? i + 1 }}]</span>
                        <span v-if="s.doc_name" class="refs-doc">{{ s.doc_name }}</span>
                      </div>
                      <div class="refs-text">{{ truncateText(s.text) }}</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 消息底栏操作条：复制 / 评价 / 失败重试 -->
              <div v-if="msg.role === 'assistant' && !msg.streaming" class="msg-actions">
                <template v-if="!msg.failed && !msg.stopped && msg.content">
                  <button
                    v-for="rating in ratings"
                    :key="rating.value"
                    type="button"
                    class="act-btn feedback-btn"
                    :aria-pressed="msg.feedback === rating.value"
                    :disabled="chat.sending || msg.ratingBusy"
                    @click="handleFeedback(msg, rating.value)"
                  >
                    <el-icon v-if="rating.value === 1"><Select /></el-icon>
                    <el-icon v-else><CloseBold /></el-icon>
                    <span>{{ rating.label }}</span>
                  </button>
                  <span v-if="msg.ratingBusy" class="feedback-saving" role="status">正在保存评价…</span>
                </template>

                <button type="button" class="act-btn" @click="copyMessage(msg)">
                  <el-icon><CopyDocument /></el-icon>
                  <span>复制</span>
                </button>

                <button v-if="msg.failed" type="button" class="act-btn retry" @click="retryMessage(msg, idx)">
                  <el-icon><RefreshLeft /></el-icon>
                  <span>重试本轮</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 悬浮跳至最新气泡 -->
        <transition name="fade-slide">
          <button v-if="showJumpBtn" type="button" class="jump-latest" @click="jumpToLatest">
            <span>跳至最新消息</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
        </transition>

        <!-- 输入控制区：弹性视口内吸底，抗缩放 -->
        <div class="chat-input-wrapper">
          <!-- 常用提问胶囊条 -->
          <div v-if="knowledgeQuestions.length && chat.messages.length" class="quick-questions" aria-label="知识库常用问题">
            <button
              v-for="question in knowledgeQuestions"
              :key="question"
              type="button"
              class="quick-question-chip"
              :disabled="chat.sending"
              @click="useSuggestion(question)"
            >
              <el-icon class="q-icon"><ChatDotRound /></el-icon>
              <span>{{ question }}</span>
            </button>
          </div>

          <div class="input-card">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="向学智汇提问，或探讨知识库内的专业文档... (Enter 发送)"
              aria-label="问题内容"
              :disabled="chat.sending"
              class="custom-textarea"
              @keydown.enter.exact="onEnterKey"
            />

            <div class="input-toolbar">
              <div class="input-hints">
                <span class="key-badge">Enter</span> 发送
                <span class="sep">/</span>
                <span class="key-badge">Shift + Enter</span> 换行
              </div>
              <el-button
                :type="chat.sending ? 'warning' : 'primary'"
                :plain="chat.sending"
                class="send-btn"
                :disabled="!chat.sending && !inputText.trim()"
                @click="chat.sending ? handleStop() : handleSend()"
              >
                <span>{{ chat.sending ? '停止生成' : '发送问题' }}</span>
                <el-icon class="send-icon">
                  <VideoPause v-if="chat.sending" />
                  <Promotion v-else />
                </el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, nextTick, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowRight,
  CloseBold,
  ChatDotRound,
  ChatLineSquare,
  CopyDocument,
  Cpu,
  Document,
  Folder,
  Opportunity,
  Plus,
  Promotion,
  Search,
  Select,
  WarningFilled,
  RefreshLeft,
  VideoPause,
} from '@element-plus/icons-vue'
import { renderMarkdown } from '../utils/markdown'
import { readDraft, writeDraft, writeSessionSel, resolveSessionKb, resolveDraftKb } from '../utils/kbMemory'
import { listMyKnowledge, listDocs, listKnowledgeQuestions } from '../api/knowledge'
import { downloadConversation, readInputDraft, writeInputDraft, visibleSessions } from '../utils/chatConvenience'
import { getSettings } from '../api/settings'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'

const authStore = useAuthStore()
// 当前流式请求的中止控制器：切路由/卸载时中断 fetch，不再向已卸载组件写消息
let streamAbort = null
// 区分「用户点停止」与「离开页面」两种中断：停止保留内容且不算失败
const stopRequested = ref(false)
onBeforeUnmount(() => {
  if (streamAbort) streamAbort.abort()
})
const chat = useChatStore()
const route = useRoute()
const router = useRouter()

const inputText = ref('')
const knowledgeId = ref('')
const kbList = ref([])
const kbLoading = ref(false)
const sessionSearch = ref('')
const showSessions = ref(false)
const filteredSessions = computed(() => visibleSessions(chat.sessions, sessionSearch.value))
const pinBusy = ref('')
const ratings = [{ value: 1, label: '有帮助' }, { value: -1, label: '没帮助' }]
const knowledgeQuestions = ref([])
let questionRequest = 0

watch(knowledgeId, async (id) => {
  const request = ++questionRequest
  knowledgeQuestions.value = []
  if (!id) return
  try {
    const questions = await listKnowledgeQuestions(id)
    if (request === questionRequest) knowledgeQuestions.value = questions
  } catch { /* 失败时保留默认提问，不阻断对话 */ }
})

watch([() => authStore.user?.id, () => chat.activeSessionId], ([uid, sid]) => {
  inputText.value = readInputDraft(uid, sid)
}, { flush: 'sync', immediate: true })
watch(inputText, (text) => {
  writeInputDraft(authStore.user?.id, chat.activeSessionId, text)
}, { flush: 'sync' })

const handlePin = async (session) => {
  pinBusy.value = session.session_id
  try { await chat.pinSession(session.session_id, !session.pinned) }
  catch { /* 请求层已提示错误 */ }
  finally { pinBusy.value = '' }
}

const handleFeedback = async (message, value) => {
  if (message.ratingBusy || chat.sending) return
  message.ratingBusy = true
  try {
    await chat.rateMessage(message, message.feedback === value ? 0 : value)
    ElMessage.success(message.feedback ? '已记录评价' : '已取消评价')
  } catch (error) {
    if (!message.id) ElMessage.warning(error.message)
  } finally { message.ratingBusy = false }
}

const exportChat = (format) => {
  if (chat.sending || !chat.messages.length) return
  const session = chat.sessions.find((s) => s.session_id === chat.activeSessionId)
  downloadConversation(chat.messages, session?.title || '对话记录', format)
}

// ---- 知识库选择记忆 ----
// 会话级：切回会话恢复该会话上次的选择（本地覆盖 > 最后一条 assistant 消息所用库）
// 空会话草稿：新开空对话默认上次空对话的选择，空会话发送定型后草稿清空
const kbNamespace = () => String(authStore.user?.id ?? 'guest')

const restoreKnowledge = () => {
  const sid = chat.activeSessionId
  if (!sid) {
    knowledgeId.value = ''
  } else if (chat.messages.length) {
    knowledgeId.value = resolveSessionKb({
      uid: kbNamespace(),
      sessionId: sid,
      messages: chat.messages,
      kbList: kbList.value,
    })
  } else {
    knowledgeId.value = resolveDraftKb({ uid: kbNamespace(), kbList: kbList.value })
  }
}

const onKnowledgeChange = (value) => {
  const sid = chat.activeSessionId
  if (!sid) return
  const v = value ? String(value) : ''
  if (chat.messages.length) writeSessionSel(kbNamespace(), sid, v)
  else writeDraft(kbNamespace(), v)
}

const messageAreaRef = ref(null)
// 参考文献默认折叠（来自用户设置，1=折叠 0=展开）
const refsCollapsedDefault = ref(true)

const defaultSuggestions = [
  '请总结一下当前知识库的核心内容',
  '知识库里的文档有哪些关键要点？',
  '你能帮我解答关于学习与工作的常见疑问吗？',
]
const suggestions = computed(() => knowledgeQuestions.value.length ? knowledgeQuestions.value : defaultSuggestions)

const userAvatarText = computed(() => {
  const name = authStore.user?.userName || authStore.user?.userAccount || '我'
  return name.charAt(0).toUpperCase()
})

// 用户上翻（距底 > 60px）时暂停自动跟随，避免流式输出把历史内容拽走
const followBottom = ref(true)
const showJumpBtn = ref(false)

const onScroll = () => {
  const el = messageAreaRef.value
  if (!el) return
  const dist = el.scrollHeight - el.scrollTop - el.clientHeight
  followBottom.value = dist < 60
  showJumpBtn.value = dist >= 60 && chat.messages.length > 0
}

const jumpToLatest = () => {
  followBottom.value = true
  showJumpBtn.value = false
  scrollToBottom(true)
}

const scrollToBottom = (force = false) => {
  nextTick(() => {
    const el = messageAreaRef.value
    if (!el || (!force && !followBottom.value)) return
    el.scrollTop = el.scrollHeight
  })
}

// 中文输入法组词确认的回车只选字，不发送
const onEnterKey = (e) => {
  if (e.isComposing || e.keyCode === 229) return
  e.preventDefault()
  handleSend()
}

// 气泡 HTML：Markdown 渲染（utils/markdown 内置 [n] 引用高亮与 XSS 消毒）；
// 流式中把光标拼进 HTML 尾部，闪烁跟随最后一个字符
const bubbleHtmlOf = (msg) => {
  const html = renderMarkdown(msg.content)
  return msg.streaming ? `${html}<span class="typing-cursor"></span>` : html
}

// 连续同人消息：不重复头像，气泡对齐到头像下方区域
const isContMsg = (idx) =>
  idx > 0 && chat.messages[idx - 1]?.role === chat.messages[idx]?.role

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
    // 列表就绪后校正当前选择：记忆的库已删除/无权限则回退为空
    if (knowledgeId.value && !kbList.value.some((kb) => String(kb.id) === String(knowledgeId.value))) {
      knowledgeId.value = ''
    }
  } finally {
    kbLoading.value = false
  }
}

const useSuggestion = (tip) => {
  if (chat.sending) return
  inputText.value = tip
  handleSend()
}

const sendQuery = async (text) => {
  followBottom.value = true
  showJumpBtn.value = false
  stopRequested.value = false
  const wasEmptySession = chat.messages.length === 0 // 空会话本次发送即定型
  chat.pushUserMessage(text)
  chat.pushAssistantPlaceholder()
  chat.sending = true
  scrollToBottom(true)

  const body = { session_id: chat.activeSessionId, query: text }
  if (knowledgeId.value) body.knowledge_id = knowledgeId.value

  streamAbort = new AbortController()
  let finished = false
  try {
    // POST + JSON body（改自 GET query：提问全文不落 nginx/Java 访问日志）
    const resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { Authorization: `Bearer ${authStore.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: streamAbort.signal,
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
          // 空会话已定型：按规则清空草稿，之后新开的空对话知识库选择为空
          if (wasEmptySession) writeDraft(kbNamespace(), '')
          await chat.finishStream(payload, true)
          await enrichSourceNames(chat.messages[chat.messages.length - 1])
          scrollToBottom()
        } else if (payload.type === 'error') {
          throw new Error(payload.message || '对话出错')
        }
      }
    }
    if (!finished) throw new Error('回答连接中断，请重试')
    await chat.syncMessageIds().catch(() => {})
  } catch (e) {
    finished = false
    if (e.name === 'AbortError') {
      // 用户点「停止」保留已生成内容且不算失败；离开页面的中断不提示
      chat.failStream(stopRequested.value ? '已停止生成' : '已离开对话', stopRequested.value)
    } else {
      chat.failStream(e.message || '对话失败，请稍后再试')
      ElMessage.error(e.message || '对话失败，请稍后再试')
    }
  } finally {
    streamAbort = null
    chat.sending = false
    scrollToBottom()
  }
  return finished
}

const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text || chat.sending) return

  const sessionId = chat.activeSessionId
  const uid = authStore.user?.id
  const original = inputText.value
  const succeeded = await sendQuery(text)
  if (succeeded && sessionId === chat.activeSessionId && uid === authStore.user?.id && inputText.value === original) {
    inputText.value = ''
  }
}

// 停止生成：中断流式请求，已生成内容保留（streaming 态发送按钮触发）
const handleStop = () => {
  if (!chat.sending || !streamAbort) return
  stopRequested.value = true
  streamAbort.abort()
}

// 失败重试：失败轮次后端未落库，本地移除该轮并原样重发
const retryMessage = (msg, idx) => {
  if (chat.sending) return
  const userMsg = chat.messages[idx - 1]
  if (!userMsg || userMsg.role !== 'user' || !userMsg.content) return
  chat.messages.splice(idx - 1, 2)
  sendQuery(userMsg.content)
}

// 复制回答（HTTP 非安全上下文无 navigator.clipboard，降级 execCommand）
const copyMessage = async (msg) => {
  const text = msg.content
  if (!text) return
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      ta.remove()
    }
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

// 正文引用编号 [n] 点击：展开引用区，滚动定位并闪烁高亮对应条目
const onMessageAreaClick = (e) => {
  const citeEl = e.target.closest('.cite')
  if (!citeEl) return
  const row = citeEl.closest('.msg-row')
  const idx = row ? Number(row.dataset.midx) : NaN
  const msg = chat.messages[idx]
  if (!msg?.sources?.length) return
  const no = (citeEl.textContent.match(/\d+/) || [])[0]
  if (!no) return
  msg.refsOpen = true
  nextTick(() => {
    const target = messageAreaRef.value?.querySelector(
      `.msg-row[data-midx="${idx}"] .ref-item[data-ref-no="${no}"]`
    )
    if (!target) return
    target.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    target.classList.add('ref-flash')
    setTimeout(() => target.classList.remove('ref-flash'), 1600)
  })
}

const handleNewSession = () => {
  if (chat.sending) return
  showSessions.value = false
  chat.newSession()
  restoreKnowledge()
}

const handleOpenSession = async (s) => {
  if (chat.sending) return
  showSessions.value = false
  await chat.openSession(s.session_id)
  restoreKnowledge()
  enrichHistorySources()
  scrollToBottom(true)
}

const handleDeleteSession = async (s) => {
  if (chat.sending) return
  await ElMessageBox.confirm(`确定删除会话「${s.title}」吗？历史将不可恢复。`, '删除确认', { type: 'warning' })
  const removedId = s.session_id
  await chat.deleteSession(removedId)
  writeInputDraft(authStore.user?.id, removedId, '')
  writeSessionSel(kbNamespace(), removedId, '') // 清理被删会话的本地记忆
  restoreKnowledge()
  enrichHistorySources()
  scrollToBottom()
}

const handleRenameSession = async (s) => {
  if (chat.sending) return
  try {
    const { value } = await ElMessageBox.prompt('为会话设置一个新的名称', '重命名会话', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: s.title || '',
      inputPlaceholder: '请输入会话名称',
      inputValidator: (v) => {
        const t = (v || '').trim()
        if (!t) return '会话名称不能为空'
        if (t.length > 64) return '不能超过 64 个字符'
        return true
      },
    })
    await chat.renameSession(s.session_id, value.trim())
  } catch (e) {
    // 取消输入静默处理；接口失败已由 axios 拦截器统一提示
  }
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
    restoreKnowledge()
    enrichHistorySources()
    scrollToBottom(true)
  } else {
    chat.newSession(id && readInputDraft(authStore.user?.id, id) ? id : undefined)
    restoreKnowledge()
  }
}

onMounted(() => {
  scrollToBottom()
  if (authStore.isLogin) {
    // 先就绪知识库列表再初始化会话，记忆值可被校验；列表加载失败不阻塞会话恢复
    loadKnowledge()
      .catch(() => {})
      .finally(() => initLoggedIn())
  } else {
    loadKnowledge()
    chat.resetForGuest()
    restoreKnowledge()
  }
})

watch(
  () => authStore.isLogin,
  (login) => {
    if (login) {
      loadKnowledge()
        .catch(() => {})
        .finally(() => initLoggedIn())
    } else {
      chat.resetForGuest()
      knowledgeId.value = ''
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
      scrollToBottom(true)
    } else {
      chat.newSession()
    }
    restoreKnowledge()
  }
)
</script>

<style scoped>
/* ────────── 全局抗缩放设计令牌映射与基准 ────────── */
.chat-page {
  height: calc(100dvh - 120px);
  min-height: 480px;
  display: flex;
  flex-direction: column;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  position: relative;
}

/* ────────── 头部顶栏（弹性收折） ────────── */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: clamp(10px, 1.5vw, 18px);
  padding: 12px clamp(14px, 1.8vw, 24px);
  background: var(--card);
  border-bottom: 1px solid var(--line);
  flex-wrap: wrap;
  flex-shrink: 0;
}

.header-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon-box {
  width: 36px;
  height: 36px;
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

.title-meta {
  display: flex;
  flex-direction: column;
}

.title-meta h2 {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
  letter-spacing: -0.01em;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}

.action-btn {
  border-radius: var(--r3);
  border-color: var(--line);
  color: var(--t2);
  min-height: 36px;
}

.session-toggle {
  display: none;
}

.header-right :deep(.el-button + .el-button) {
  margin-inline-start: 0;
}

.btn-icon-right {
  margin-left: 6px;
  font-size: var(--text-muted);
}

.kb-select {
  width: clamp(200px, 20vw, 300px);
}

.kb-select :deep(.el-input__wrapper) {
  border-radius: var(--r3);
}

.select-icon {
  color: var(--brand);
}

.kb-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}

.kb-option-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ────────── 主布局主体 ────────── */
.chat-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

/* ────────── 左侧会话侧栏（比例弹性宽度 clamp） ────────── */
.session-panel {
  width: clamp(230px, 19vw, 300px);
  flex-shrink: 0;
  border-right: 1px solid var(--line);
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--bg);
  min-height: 0;
  overflow: hidden;
}

.new-chat-btn {
  width: 100%;
  height: 40px;
  font-weight: 600;
  border-radius: var(--r3);
  background: var(--brand);
  border-color: var(--brand);
  box-shadow: 0 2px 6px var(--tint-strong);
  flex-shrink: 0;
}

.new-chat-btn:hover {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
}

.search-box-wrap {
  flex-shrink: 0;
}

.session-search-input :deep(.el-input__wrapper) {
  border-radius: var(--r3);
  background: var(--card);
  box-shadow: 0 0 0 1px var(--line) inset;
}

.session-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 4px 0;
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--t3);
  flex-shrink: 0;
}

.count-tag {
  background: color-mix(in srgb, var(--t4) 12%, transparent);
  color: var(--t3);
  border: none;
}

.session-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  min-height: 0;
  padding-right: 2px;
}

.session-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: var(--r3);
  cursor: pointer;
  background: var(--card);
  border: 1px solid transparent;
  box-shadow: 0 1px 2px color-mix(in srgb, var(--t1) 3%, transparent);
  transition: all 0.2s ease;
}

.session-item:hover {
  background: var(--card);
  border-color: var(--tint-strong);
  box-shadow: var(--shadow-card);
}

.session-item.active {
  background: var(--tint);
  border-color: var(--brand);
}

.session-open {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  text-align: left;
  background: transparent;
  border: 0;
  padding: 0;
  cursor: pointer;
}

.session-lead-badge {
  width: 28px;
  height: 28px;
  border-radius: var(--r3);
  background: color-mix(in srgb, var(--t4) 10%, transparent);
  color: var(--t3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-body);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.session-item.active .session-lead-badge {
  background: var(--brand);
  color: var(--card);
}

.session-lead-badge.is-pinned {
  color: var(--brand);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  display: block;
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item.active .session-title {
  color: var(--brand);
}

.session-sub {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
  font-size: var(--text-muted);
  color: var(--t4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pinned-tag {
  color: var(--brand);
  font-weight: 600;
}

.dot-sep {
  opacity: 0.6;
}

.session-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  border-top: 1px solid var(--line);
  padding-top: 4px;
}

.session-tools :deep(.el-button) {
  margin: 0;
  padding: 0 4px;
  min-height: 28px;
  font-size: var(--text-muted);
  color: var(--t3);
}

.session-tools :deep(.el-button:hover) {
  color: var(--brand);
}

.session-tools :deep(.danger-btn:hover) {
  color: var(--el-color-danger);
}

.panel-tip {
  padding: 32px 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--t4);
  font-size: var(--text-muted);
  gap: 8px;
}

.tip-icon {
  font-size: var(--text-num);
  opacity: 0.4;
}

/* ────────── 右侧核心会话区 ────────── */
.chat-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow-wrap: anywhere;
  position: relative;
  background: var(--card);
}

.message-area {
  flex: 1;
  overflow-y: auto;
  padding: clamp(16px, 2vw, 28px) clamp(16px, 2.5vw, 36px);
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-height: 0;
}

/* 欢迎空态 */
.chat-welcome {
  margin: auto;
  max-width: 620px;
  width: 100%;
  text-align: center;
  padding: 24px 16px;
}

.welcome-icon-box {
  width: 58px;
  height: 58px;
  margin: 0 auto 16px;
  border-radius: var(--r1);
  background: var(--tint);
  color: var(--brand);
  border: 1px solid var(--tint-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-num);
  box-shadow: 0 4px 12px var(--tint);
}

.chat-welcome h3 {
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
  margin: 0 0 8px;
}

.welcome-desc {
  font-size: var(--text-body);
  color: var(--t3);
  line-height: 1.6;
  margin: 0 auto 24px;
  max-width: 480px;
}

.welcome-suggestions-grid {
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--r3);
  padding: 16px;
  text-align: left;
}

.suggest-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--brand);
  margin-bottom: 12px;
}

.suggest-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.suggest-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r3);
  font-size: var(--text-body);
  color: var(--t2);
  cursor: pointer;
  transition: all 0.2s ease;
}

.suggest-chip:hover {
  background: var(--tint);
  border-color: var(--tint-strong);
  color: var(--brand);
  transform: translateX(2px);
}

.chip-arrow {
  font-size: var(--text-muted);
  color: var(--t4);
  flex-shrink: 0;
}

/* ────────── 消息流气泡 ────────── */
.msg-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  max-width: 100%;
}

.msg-row.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  flex-shrink: 0;
  box-shadow: 0 2px 6px color-mix(in srgb, var(--t1) 6%, transparent);
}

.msg-avatar.user {
  background: var(--brand);
  color: var(--card);
  font-weight: 700;
  font-size: var(--text-body);
}

.msg-avatar.assistant {
  background: var(--t2);
  color: var(--card);
  font-size: var(--text-heading);
}

.msg-bubble-wrap {
  max-width: min(85%, 820px);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.msg-bubble-wrap.user {
  align-items: flex-end;
}

/* 连续同人消息缩进 */
.msg-row.cont:not(.user) .msg-bubble-wrap {
  margin-left: 50px;
}

.msg-row.cont.user .msg-bubble-wrap {
  margin-right: 50px;
}

.msg-bubble {
  padding: 14px 18px;
  border-radius: var(--r1);
  font-size: var(--text-body);
  line-height: 1.7;
  word-break: break-word;
  position: relative;
}

.msg-bubble.user {
  background: var(--brand);
  color: var(--card);
  border-top-right-radius: 4px;
  box-shadow: 0 2px 8px var(--tint-strong);
}

.msg-bubble.assistant {
  background: var(--bg);
  color: var(--t1);
  border: 1px solid var(--line);
  border-top-left-radius: 4px;
}

/* 意图与来源标签 */
.msg-provenance {
  margin-bottom: 8px;
}

.provenance-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--r-pill);
  font-size: var(--text-muted);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.provenance-pill.primary {
  background: var(--tint);
  color: var(--brand);
  border: 1px solid var(--tint-strong);
}

.provenance-pill.info {
  background: color-mix(in srgb, var(--t4) 12%, transparent);
  color: var(--t3);
}

.provenance-pill.success {
  background: color-mix(in srgb, var(--el-color-success) 12%, transparent);
  color: var(--el-color-success-dark-2);
}

.provenance-pill.warning {
  background: color-mix(in srgb, var(--el-color-warning) 12%, transparent);
  color: var(--el-color-warning-dark-2);
}

/* 思考状态脉冲指示 */
.thinking-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--text-body);
  color: var(--t3);
  padding: 4px 0;
}

.thinking-dots {
  display: inline-flex;
  gap: 4px;
}

.thinking-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  animation: thinking-pulse 1.2s ease-in-out infinite;
}

.thinking-dots i:nth-child(2) { animation-delay: 0.15s; }
.thinking-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes thinking-pulse {
  0%, 70%, 100% { opacity: 0.25; transform: scale(0.85); }
  35% { opacity: 1; transform: scale(1.1); }
}

/* Markdown 排版增强 */
.msg-text :deep(p) {
  margin: 0 0 10px;
}

.msg-text :deep(p:last-child) {
  margin-bottom: 0;
}

.msg-text :deep(h1),
.msg-text :deep(h2),
.msg-text :deep(h3),
.msg-text :deep(h4) {
  margin: 14px 0 8px;
  font-weight: 700;
  color: var(--t1);
  line-height: 1.4;
}

.msg-text :deep(h1:first-child),
.msg-text :deep(h2:first-child),
.msg-text :deep(h3:first-child) {
  margin-top: 0;
}

.msg-text :deep(ul),
.msg-text :deep(ol) {
  margin: 6px 0 10px;
  padding-left: 20px;
}

.msg-text :deep(li) {
  margin: 4px 0;
}

.msg-text :deep(table) {
  border-collapse: collapse;
  margin: 12px 0;
  font-size: var(--text-body);
  width: 100%;
  overflow-x: auto;
  display: block;
}

.msg-text :deep(th),
.msg-text :deep(td) {
  border: 1px solid var(--line);
  padding: 6px 12px;
  text-align: left;
}

.msg-text :deep(th) {
  background: var(--line);
  font-weight: 600;
  color: var(--t2);
}

.msg-text :deep(pre) {
  margin: 10px 0;
  padding: 12px 14px;
  background: var(--t1);
  color: var(--bg);
  border-radius: var(--r3);
  overflow-x: auto;
  font-size: var(--text-body);
  line-height: 1.6;
}

.msg-text :deep(code) {
  font-family: 'Space Grotesk', Consolas, monospace;
}

.msg-text :deep(:not(pre) > code) {
  background: var(--tint);
  color: var(--brand);
  border-radius: var(--r3);
  padding: 2px 6px;
  font-size: var(--text-body);
}

.msg-text :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 0 4px 14px;
  border-left: 3px solid var(--brand);
  color: var(--t3);
  background: var(--tint);
  border-radius: 0 var(--r3) var(--r3) 0;
}

.msg-bubble.user .msg-text :deep(:not(pre) > code) {
  background: color-mix(in srgb, var(--card) 20%, transparent);
  color: var(--card);
}

/* 引用高亮与跳跃定位 */
.cite {
  display: inline-block;
  margin: 0 2px;
  padding: 0 4px;
  border-radius: var(--r3);
  background: var(--tint-strong);
  color: var(--brand);
  font-weight: 600;
  font-size: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

.cite:hover {
  background: var(--brand);
  color: var(--card);
}

.msg-bubble.user .cite {
  background: color-mix(in srgb, var(--card) 25%, transparent);
  color: var(--card);
}

/* 思考过程 */
.msg-thinking {
  margin-top: 12px;
  padding: 10px 14px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r3);
  font-size: var(--text-muted);
}

.thinking-summary {
  cursor: pointer;
  user-select: none;
  font-weight: 600;
  color: var(--t3);
}

.thinking-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.thinking-list {
  margin: 8px 0 0;
  padding-left: 18px;
  line-height: 1.7;
  color: var(--t3);
}

/* 参考文献展示区 */
.msg-refs {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.refs-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 28px;
  text-align: start;
  color: var(--t2);
  font-weight: 600;
  font-size: var(--text-muted);
  cursor: pointer;
  user-select: none;
}

.refs-title-left {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--brand);
}

.refs-arrow {
  color: var(--t4);
}

.refs-list-box {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ref-item {
  padding: 10px 12px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r3);
  font-size: var(--text-muted);
  transition: all 0.3s ease;
}

.ref-lead {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.refs-num {
  font-weight: 700;
  color: var(--brand);
}

.refs-doc {
  font-weight: 600;
  color: var(--t1);
}

.refs-text {
  color: var(--t3);
  line-height: 1.6;
}

.ref-item.ref-flash {
  background: var(--tint);
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--tint-strong);
}

/* 消息尾注状态 */
.stopped-note {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 3px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--card);
  font-size: var(--text-muted);
  color: var(--t4);
}

.failed-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  padding: 8px 12px;
  border: 1px solid var(--el-color-danger-light-8);
  border-radius: var(--r3);
  background: var(--el-color-danger-light-9);
  font-size: var(--text-muted);
  color: var(--el-color-danger);
}

/* 消息底栏快捷动作条 */
.msg-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.act-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--r3);
  background: transparent;
  color: var(--t3);
  font-size: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

.act-btn:hover {
  color: var(--brand);
  background: var(--tint);
}

.act-btn[aria-pressed="true"] {
  color: var(--brand);
  background: var(--tint-strong);
  font-weight: 600;
}

.act-btn.retry {
  color: var(--el-color-warning-dark-2);
}

.act-btn.retry:hover {
  background: color-mix(in srgb, var(--el-color-warning) 12%, transparent);
}

.feedback-saving {
  font-size: var(--text-muted);
  color: var(--t4);
}

/* 打字机闪烁光标 */
.typing-cursor {
  display: inline-block;
  width: 6px;
  height: 15px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--brand);
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 跳至最新悬浮气泡 */
.jump-latest {
  position: absolute;
  left: 50%;
  bottom: clamp(100px, 14vh, 120px);
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--card);
  color: var(--t2);
  font-size: var(--text-body);
  font-weight: 500;
  cursor: pointer;
  box-shadow: var(--shadow-pop);
  z-index: 10;
  transition: all 0.2s ease;
}

.jump-latest:hover {
  color: var(--brand);
  border-color: var(--tint-strong);
  transform: translateX(-50%) translateY(-2px);
}

/* ────────── 输入控制底栏（弹性边界防截断） ────────── */
.chat-input-wrapper {
  padding: 12px clamp(16px, 2.5vw, 36px) clamp(16px, 2vw, 24px);
  background: var(--card);
  border-top: 1px solid var(--line);
  flex-shrink: 0;
}

/* 快捷推荐提问水平滑动条 */
.quick-questions {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 8px;
  scrollbar-width: none;
}

.quick-questions::-webkit-scrollbar {
  display: none;
}

.quick-question-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  font-size: var(--text-muted);
  color: var(--t2);
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.quick-question-chip:hover {
  background: var(--tint);
  border-color: var(--tint-strong);
  color: var(--brand);
}

.q-icon {
  color: var(--brand);
}

.input-card {
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: 10px 14px 8px;
  background: var(--card);
  box-shadow: 0 1px 3px color-mix(in srgb, var(--t1) 4%, transparent);
  transition: all 0.2s ease;
}

.input-card:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--tint-strong);
}

.custom-textarea :deep(.el-textarea__inner) {
  border: 0 !important;
  box-shadow: none !important;
  padding: 2px 0 !important;
  font-size: var(--text-body);
  line-height: 1.6;
  color: var(--t1);
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
  flex-wrap: wrap;
  gap: 8px;
}

.input-hints {
  font-size: var(--text-muted);
  color: var(--t4);
  display: flex;
  align-items: center;
  gap: 4px;
}

.key-badge {
  display: inline-block;
  padding: 1px 5px;
  background: color-mix(in srgb, var(--t4) 12%, transparent);
  border-radius: var(--r3);
  font-family: monospace;
  color: var(--t3);
}

.sep {
  opacity: 0.5;
}

.send-btn {
  border-radius: var(--r3);
  font-weight: 600;
  padding: 8px 18px;
  min-height: 36px;
}

.send-icon {
  margin-left: 6px;
}

/* ────────── 自定义细滚动条 ────────── */
.custom-scroll::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.custom-scroll::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--t4) 30%, transparent);
  border-radius: var(--r-pill);
}
.custom-scroll::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--t4) 50%, transparent);
}

@container (max-width: 720px) {
  .session-toggle {
    display: inline-flex;
  }

  .chat-body {
    position: relative;
  }

  .session-panel {
    display: none;
  }

  .session-panel.is-open {
    position: absolute;
    inset: 0 auto 0 0;
    z-index: 2;
    display: flex;
    width: min(320px, 100%);
    box-shadow: var(--shadow-pop);
  }

  .session-panel.is-open + .chat-column {
    visibility: hidden;
  }

  .kb-select {
    width: 100%;
  }
}

@container (max-width: 420px) {
  .input-hints {
    display: none;
  }

  .action-btn {
    padding-inline: 8px;
  }
}

@media (max-height: 680px) {
  .chat-page {
    min-height: 420px;
  }
  .message-area {
    padding: 12px 16px;
    gap: 14px;
  }
  .chat-input-wrapper {
    padding: 8px 16px 12px;
  }
}
</style>
