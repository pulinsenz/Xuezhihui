<template>
  <div class="chat-page">
    <!-- 顶部：标题 + 知识库选择与状态 -->
    <div class="chat-header">
      <div class="header-main">
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
              <el-tooltip v-if="!chat.sending" content="重命名会话" placement="top">
                <button type="button" class="session-edit" @click.stop="handleRenameSession(s)">
                  <el-icon><EditPen /></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip v-if="!chat.sending" content="删除会话" placement="top">
                <button type="button" class="session-del" @click.stop="handleDeleteSession(s)">
                  <el-icon><Delete /></el-icon>
                </button>
              </el-tooltip>
            </div>
          </div>
          <div v-else class="panel-tip">暂无历史会话，点击上方开启新对话</div>
        </template>
      </aside>

      <!-- 右侧：对话区 -->
      <div class="chat-column">
        <!-- 消息流展示区；cite 点击委托到容器 -->
        <div ref="messageAreaRef" class="message-area" @scroll="onScroll" @click="onMessageAreaClick">
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
            :class="[msg.role, { cont: isContMsg(idx) }]"
            :data-midx="idx"
          >
            <!-- 连续同人消息不重复头像，气泡对齐到头像下方区域 -->
            <el-avatar v-if="!isContMsg(idx)" :size="36" class="msg-avatar" :class="msg.role">
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
                  <!-- 意图与数据来源标识：思考阶段先不显示，避免与「正在思考…」抢占 -->
                  <div v-if="provenanceOf(msg).label && (msg.content || !msg.streaming)" class="msg-provenance">
                    <el-tag size="small" :type="provenanceOf(msg).type" effect="plain" class="route-tag">
                      {{ provenanceOf(msg).label }}
                    </el-tag>
                  </div>
                  <!-- 首个 token 到达前的思考中指示 -->
                  <span v-if="msg.streaming && !msg.content" class="thinking-indicator">
                    <span class="thinking-dots"><i /><i /><i /></span>
                    正在思考…
                  </span>
                </template>

                <div v-if="msg.content" class="msg-text" v-html="bubbleHtmlOf(msg)" />

                <!-- 停止 / 失败尾注 -->
                <div v-if="msg.stopped" class="stopped-note">⏸ 已停止生成</div>
                <div v-else-if="msg.errorText" class="failed-note">⚠️ {{ msg.errorText }}</div>

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

              <!-- 消息操作条：复制 / 失败重试 -->
              <div v-if="msg.role === 'assistant' && !msg.streaming" class="msg-actions">
                <button type="button" class="act-btn" @click="copyMessage(msg)">
                  <el-icon><CopyDocument /></el-icon>
                  <span>复制</span>
                </button>
                <button v-if="msg.failed" type="button" class="act-btn retry" @click="retryMessage(msg, idx)">
                  <el-icon><RefreshLeft /></el-icon>
                  <span>重试</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 用户上翻后停止自动跟随，提供跳至最新入口 -->
        <button v-if="showJumpBtn" type="button" class="jump-latest" @click="jumpToLatest">
          <span>跳至最新</span>
          <el-icon><ArrowDown /></el-icon>
        </button>

        <!-- 输入控制区 -->
        <div class="chat-input-wrapper">
          <div class="input-card">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="输入你的问题..."
              :disabled="chat.sending"
              class="custom-textarea"
              @keydown.enter.exact.prevent="onEnterKey"
            />
            <div class="input-toolbar">
              <div class="input-hints">
                <span>Enter 发送 / Shift+Enter 换行</span>
              </div>
              <el-button
                :type="chat.sending ? 'warning' : 'primary'"
                :plain="chat.sending"
                class="send-btn"
                :disabled="!chat.sending && !inputText.trim()"
                @click="chat.sending ? handleStop() : handleSend()"
              >
                <span>{{ chat.sending ? '停止' : '发送' }}</span>
                <el-icon class="send-icon">
                  <VideoPause v-if="chat.sending" />
                  <Promotion v-else />
                </el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, nextTick, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ChatDotRound,
  ChatLineSquare,
  CopyDocument,
  Cpu,
  Delete,
  Document,
  EditPen,
  Folder,
  Opportunity,
  Plus,
  Promotion,
  RefreshLeft,
  VideoPause,
} from '@element-plus/icons-vue'
import { renderMarkdown } from '../utils/markdown'
import { readDraft, writeDraft, writeSessionSel, resolveSessionKb, resolveDraftKb, kbIdInList } from '../utils/kbMemory'
import { listMyKnowledge, listDocs } from '../api/knowledge'
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

const suggestions = [
  '请总结一下当前知识库的核心内容',
  '知识库里的文档有哪些关键要点？',
  '你能帮我解答关于学习与工作的常见疑问吗？',
]

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
  inputText.value = tip
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
}

const handleSend = () => {
  const text = inputText.value.trim()
  if (!text || chat.sending) return

  inputText.value = ''
  sendQuery(text)
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
  chat.newSession()
  restoreKnowledge()
}

const handleOpenSession = async (s) => {
  if (chat.sending) return
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
    chat.newSession()
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
.chat-page {
  height: calc(100vh - 144px);
  min-height: 580px;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e8eef1;
  border-radius: 16px;
  corner-shape: squircle;
  box-shadow: var(--shadow-card);
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

.header-main h2 {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
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
  background: var(--bg);
  overflow-y: auto;
}

.new-chat-btn {
  width: 100%;
  height: 40px;
  font-weight: 600;
}

.session-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--t3);
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
  box-shadow: var(--shadow-card);
}

.session-item.active {
  background: #ffffff;
  border: 1px solid rgba(20, 184, 166, 0.35);
}

.session-lead-icon {
  font-size: 16px;
  color: var(--t4);
  flex-shrink: 0;
}

.session-item.active .session-lead-icon {
  color: var(--brand);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: var(--text-body);
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 72px;
}

.session-item.active .session-title {
  color: var(--brand);
}

.session-sub {
  margin-top: 3px;
  font-size: var(--text-muted);
  color: var(--t4);
}

/* 行内操作按钮：视觉图标 15px，热区 28px，留出间距防误触 */
.session-edit,
.session-del {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #cbd5e1;
  font-size: 16px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease, color 0.15s ease, background 0.15s ease;
}

.session-edit {
  right: 40px;
}

.session-del {
  right: 4px;
}

.session-item:hover .session-edit,
.session-item:hover .session-del,
.session-edit:focus-visible,
.session-del:focus-visible {
  opacity: 1;
}

.session-edit:hover {
  color: var(--brand);
  background: #f1f5f9;
}

.session-del:hover {
  color: #ef4444;
  background: #fef2f2;
}

.panel-tip {
  padding: 30px 12px;
  font-size: 12px;
  color: var(--t4);
  text-align: center;
  line-height: 1.6;
}

/* 右侧对话区 */
.chat-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
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
  border-radius: 16px;
  corner-shape: squircle;
  background: rgba(15, 118, 110, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  color: var(--brand);
}

.chat-welcome h3 {
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
  margin-bottom: 8px;
}

.chat-welcome p {
  font-size: 14px;
  color: var(--t3);
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
  background: var(--bg);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  font-size: var(--text-body);
  color: var(--t2);
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.suggest-chip:hover {
  background: #f0fdfa;
  border-color: rgba(20, 184, 166, 0.4);
  color: var(--brand);
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
  background: var(--brand);
  color: #fff;
  font-weight: 600;
}

.msg-avatar.assistant {
  background: var(--t2);
  color: #fff;
  font-size: 18px;
}

.msg-bubble-wrap {
  max-width: 88%;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.msg-bubble-wrap.user {
  align-items: flex-end;
}

/* 连续同人消息：无头像行，气泡缩进对齐到首条头像正下方 */
.msg-row.cont:not(.user) .msg-bubble-wrap {
  margin-left: 50px;
}

.msg-row.cont.user .msg-bubble-wrap {
  margin-right: 50px;
}

.msg-bubble {
  padding: 14px 18px;
  border-radius: var(--r2);
  font-size: var(--text-body);
  line-height: 1.7;
  word-break: break-word;
}

.msg-bubble.user {
  background: var(--brand);
  color: #ffffff;
  border-top-right-radius: 4px;
}

.msg-bubble.assistant {
  background: var(--bg);
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
  color: var(--brand);
  font-weight: 600;
  font-size: 12px;
}

.msg-bubble.user .cite {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

/* 正文可点击：cite 有 hover 反馈 */
.msg-text .cite {
  cursor: pointer;
  transition: background 0.15s ease;
}

.msg-text .cite:hover {
  background: rgba(20, 184, 166, 0.28);
}

.msg-bubble.user .msg-text .cite {
  cursor: default;
}

.msg-bubble.user .msg-text .cite:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* Markdown 元素（v-html 内容走 :deep） */
.msg-text :deep(p) {
  margin: 0 0 8px;
}

.msg-text :deep(p:last-child) {
  margin-bottom: 0;
}

.msg-text :deep(h1),
.msg-text :deep(h2),
.msg-text :deep(h3),
.msg-text :deep(h4) {
  margin: 12px 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--t1);
  line-height: 1.5;
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
  margin: 3px 0;
}

.msg-text :deep(li > p) {
  margin: 0;
}

.msg-text :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  display: block;
  max-width: 100%;
  overflow-x: auto;
}

.msg-text :deep(th),
.msg-text :deep(td) {
  border: 1px solid var(--line);
  padding: 5px 12px;
  text-align: left;
}

.msg-text :deep(th) {
  background: #f1f5f9;
  font-weight: 600;
  color: var(--t2);
  white-space: nowrap;
}

.msg-text :deep(pre) {
  margin: 10px 0;
  padding: 10px 14px;
  background: var(--t1);
  color: var(--line);
  border-radius: 10px;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.6;
}

.msg-text :deep(code) {
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12.5px;
}

.msg-text :deep(:not(pre) > code) {
  background: rgba(20, 184, 166, 0.1);
  color: var(--brand);
  border-radius: 4px;
  padding: 1px 5px;
}

.msg-text :deep(blockquote) {
  margin: 8px 0;
  padding: 2px 0 2px 12px;
  border-left: 3px solid #cbd5e1;
  color: var(--t3);
}

.msg-text :deep(a) {
  color: var(--brand);
  font-weight: 500;
}

.msg-text :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

.msg-text :deep(hr) {
  border: 0;
  border-top: 1px solid var(--line);
  margin: 12px 0;
}

/* 用户气泡（teal 底）内的 markdown 适配 */
.msg-bubble.user .msg-text :deep(:not(pre) > code) {
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
}

.msg-bubble.user .msg-text :deep(a) {
  color: #ffffff;
  text-decoration: underline;
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
  line-height: 1.8;
  color: var(--t3);
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
  font-size: var(--text-muted);
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
  color: var(--brand);
}

.refs-doc {
  font-weight: 600;
  color: #1e293b;
}

.refs-text {
  color: var(--t3);
  line-height: 1.6;
}

.typing-cursor {
  display: inline-block;
  width: 6px;
  height: 15px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: #14b8a6;
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 首个 token 到达前的思考中指示 */
.thinking-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--t3);
}

.thinking-dots {
  display: inline-flex;
  gap: 4px;
}

.thinking-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #14b8a6;
  animation: thinking-pulse 1.2s ease-in-out infinite;
}

.thinking-dots i:nth-child(2) { animation-delay: 0.15s; }
.thinking-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes thinking-pulse {
  0%, 70%, 100% { opacity: 0.25; transform: scale(0.85); }
  35% { opacity: 1; transform: scale(1); }
}

/* 停止 / 失败尾注 */
.stopped-note {
  display: inline-flex;
  align-items: center;
  margin-top: 8px;
  padding: 3px 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 999px;
  background: var(--bg);
  font-size: 12px;
  color: var(--t4);
}

.failed-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding: 8px 12px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fef2f2;
  font-size: 12px;
  color: #ef4444;
  line-height: 1.5;
}

/* 消息操作条：常显弱化，保证触屏可达 */
.msg-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  opacity: 0.55;
  transition: opacity 0.18s ease;
}

.msg-bubble-wrap:hover .msg-actions,
.msg-actions:focus-within {
  opacity: 1;
}

.act-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--t4);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}

.act-btn:hover {
  color: var(--brand);
  background: rgba(20, 184, 166, 0.09);
}

.act-btn.retry {
  color: #b45309;
}

.act-btn.retry:hover {
  color: #92400e;
  background: rgba(245, 158, 11, 0.12);
}

/* 引用条目点击定位后的闪烁高亮 */
.ref-item.ref-flash {
  background: rgba(20, 184, 166, 0.12);
  border-color: rgba(20, 184, 166, 0.55);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.15);
}

/* 跳至最新：真浮层，唯一配重阴影的元素 */
.jump-latest {
  position: absolute;
  left: 50%;
  bottom: 96px;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 32px;
  padding: 0 14px;
  border: 1px solid #e8eef1;
  border-radius: 999px;
  background: #ffffff;
  color: var(--t2);
  font-size: var(--text-muted);
  cursor: pointer;
  box-shadow: var(--shadow-pop);
  z-index: 2;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.jump-latest:hover {
  color: var(--brand);
  border-color: rgba(20, 184, 166, 0.4);
}

/* 输入框 */
.chat-input-wrapper {
  padding: 16px 28px 24px;
  background: #ffffff;
  border-top: 1px solid rgba(226, 232, 240, 0.8);
}

.input-card {
  border: 1px solid #cbd5e1;
  border-radius: var(--r2);
  corner-shape: squircle;
  padding: 10px 14px;
  background: #ffffff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.input-card:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
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
  font-size: var(--text-muted);
  color: var(--t4);
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
  .msg-bubble-wrap {
    max-width: 92%;
  }
  .msg-row.cont:not(.user) .msg-bubble-wrap {
    margin-left: 42px;
  }
  .msg-row.cont.user .msg-bubble-wrap {
    margin-right: 42px;
  }
  .chat-input-wrapper {
    padding: 12px 16px 16px;
  }
}

/* 矮视口（小窗口/缩放）：压缩聊天页最小高度，保证输入框与发送按钮完整可见 */
@media (max-height: 700px) {
  .chat-page {
    min-height: 420px;
  }
}
</style>