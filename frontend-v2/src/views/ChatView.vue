<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { streamChat, listSessions } from '../api/chat'
import { listMyKnowledge, listDocs } from '../api/knowledge'
import { toast } from '../components/toast'
import XButton from '../components/XButton.vue'
import XIcon from '../components/XIcon.vue'
import XEmpty from '../components/XEmpty.vue'

const auth = useAuthStore()
const chat = useChatStore()

const kbList = ref([])
const knowledgeId = ref('')

const inputText = ref('')
const streamAbort = ref(null)
const stopRequested = ref(false)

// 聊天自动跟随：距底部不超过一行才算在跟着看；上翻超 3 行出「跳至最新」
const msgArea = ref(null)
const followBottom = ref(true)
const showJumpBtn = ref(false)
const FLOAT_THRESHOLD = 3 * 22

const suggestions = [
  '产品手册里退货流程是什么？',
  '网关超时怎么排查？',
  '接口鉴权 JWT 如何配置？',
]

onMounted(async () => {
  chat.newSession()
  try {
    await chat.loadSessions()
  } catch (e) { /* 未登录态由路由守卫兜底 */ }
  try {
    kbList.value = await listMyKnowledge()
  } catch (e) { /* 下拉显示空 */ }
})
onBeforeUnmount(() => streamAbort.value?.abort())

function onScroll() {
  const el = msgArea.value
  if (!el) return
  const dist = el.scrollHeight - el.scrollTop - el.clientHeight
  followBottom.value = dist <= 22
  showJumpBtn.value = dist > FLOAT_THRESHOLD
}
function scrollToBottom(force = false) {
  if (!force && !followBottom.value) return
  requestAnimationFrame(() => {
    const el = msgArea.value
    if (el) el.scrollTop = el.scrollHeight
  })
}
function jumpLatest() {
  followBottom.value = true
  showJumpBtn.value = false
  scrollToBottom(true)
}

const routeTag = computed(() => {
  const m = chat.messages[chat.messages.length - 1]
  if (m?.route === 'business') return '知识库问答'
  if (m?.route === 'other') return '通用模型回答'
  return ''
})

const kbName = (id) => kbList.value.find((x) => String(x.id) === String(id))?.name || ''

// 来源文档名补全（后端只带 doc_id 时用列表接口换名字）
const docNameMaps = {}
async function enrichSourceNames(msg) {
  if (!msg?.sources?.length || !msg.knowledge_id) return
  const kId = msg.knowledge_id
  if (!docNameMaps[kId]) {
    try {
      const docs = await listDocs(kId, 0)
      const map = {}
      for (const d of docs) map[d.id] = d.name
      docNameMaps[kId] = map
    } catch (e) { docNameMaps[kId] = {} }
  }
  for (const s of msg.sources) {
    if (s.doc_id && docNameMaps[kId][s.doc_id]) s.doc_name = docNameMaps[kId][s.doc_id]
  }
}

async function sendQuery(text) {
  followBottom.value = true
  showJumpBtn.value = false
  stopRequested.value = false
  chat.pushUserMessage(text)
  chat.pushAssistantPlaceholder()
  chat.sending = true
  scrollToBottom(true)

  streamAbort.value = new AbortController()
  let finished = false
  try {
    await streamChat({
      sessionId: chat.activeSessionId,
      query: text,
      knowledgeId: knowledgeId.value || undefined,
      token: auth.token,
      signal: streamAbort.value.signal,
      onEvent: async (payload) => {
        if (payload.type === 'token') {
          chat.appendToken(payload.content)
          scrollToBottom()
        } else if (payload.type === 'thinking') {
          chat.addThinking(payload.content || '')
          scrollToBottom()
        } else if (payload.type === 'done') {
          finished = true
          await chat.finishStream(payload)
          await enrichSourceNames(chat.messages[chat.messages.length - 1])
          scrollToBottom()
        } else if (payload.type === 'error') {
          throw new Error(payload.message || '对话出错')
        }
      },
    })
    if (!finished) await chat.finishStream({})
  } catch (e) {
    if (e.name === 'AbortError') {
      chat.failStream('已停止生成', stopRequested.value)
    } else {
      chat.failStream(e.message || '对话失败，请稍后再试')
      toast.error(e.message || '对话失败，请稍后再试')
    }
  } finally {
    streamAbort.value = null
    chat.sending = false
    scrollToBottom()
  }
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text || chat.sending) return
  inputText.value = ''
  sendQuery(text)
}

// 中文输入法组词：回车只选字不发送（组合输入中不触发）
let composing = false
function onCompositionStart() { composing = true }
function onCompositionEnd() { setTimeout(() => { composing = false }, 80) }
function onEnter() {
  if (composing) return
  handleSend()
}

// 失败重试：失败轮次后端未落库，本地移除该轮并原样重发
function retryLast(idx) {
  if (chat.sending) return
  const userMsg = chat.messages[idx - 1]
  if (!userMsg || userMsg.role !== 'user' || !userMsg.content) return
  chat.messages.splice(idx - 1, 2)
  sendQuery(userMsg.content)
}

function handleStop() {
  if (!chat.sending || !streamAbort.value) return
  stopRequested.value = true
  streamAbort.value.abort()
}

async function copyMessage(msg) {
  if (!msg.content) return
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(msg.content)
    } else {
      const ta = document.createElement('textarea')
      ta.value = msg.content
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      ta.remove()
    }
    toast.success('已复制到剪贴板')
  } catch (e) {
    toast.error('复制失败，请手动选择文本复制')
  }
}

function useSuggestion(tip) {
  inputText.value = tip
}
</script>

<template>
  <div class="chat-page">
    <aside class="chat-side">
      <XButton class="new-chat" :disabled="chat.sending" @click="chat.newSession()">
        <XIcon name="plus" :size="13" /> 开启新对话
      </XButton>
      <div class="side-title">会话时间线</div>
      <div v-if="chat.sessions.length" class="session-list">
        <div
          v-for="s in chat.sessions" :key="s.session_id"
          class="session" :class="{ active: s.session_id === chat.activeSessionId }"
          @click="chat.openSession(s.session_id)"
        >
          <b>{{ s.title || '未命名会话' }}</b>
          <span class="s-sub">{{ s.message_count }} 条消息</span>
        </div>
      </div>
      <div v-else class="panel-tip">暂无历史会话<br>点击上方开启新对话</div>
    </aside>

    <div class="chat-main">
      <div class="chat-head">
        <span class="hd-dot" />
        <b>{{ chat.messages.length === 0 ? '新对话' : '当前对话' }}</b>
        <span class="x-tag mint kb-tag">
          {{ knowledgeId ? `已挂载：${kbName(knowledgeId) || knowledgeId}` : '未挂载知识库 · 自由对话' }}
        </span>
      </div>

      <div ref="msgArea" class="chat-body" @scroll="onScroll">
        <XEmpty
          v-if="chat.messages.length === 0"
          title="你好，我是学智汇 AI 助手"
          sub="基于检索增强生成技术，支持挂载知识库回答专业问题，也可以自由提问。"
        >
          <div class="chips">
            <button v-for="tip in suggestions" :key="tip" class="chip" @click="useSuggestion(tip)">{{ tip }}</button>
          </div>
        </XEmpty>

        <div v-for="(msg, idx) in chat.messages" :key="idx" class="msg" :class="msg.role">
          <span class="m-dot" :class="msg.role" />
          <div class="msg-main">
            <div class="bubble" :class="msg.role">
              <!-- 检索过程时间线（thinking 事件流） -->
              <div v-if="msg.role === 'assistant' && msg.thinking.length" class="trace">
                <div v-for="(t, ti) in msg.thinking" :key="ti" class="t-step">
                  <span class="t-dot done" />
                  <span class="t-name">{{ t }}</span>
                  <span class="t-line" />
                </div>
                <div v-if="msg.streaming" class="t-step">
                  <span class="t-dot run" />
                  <span class="t-name"><b>生成回答</b>…</span>
                  <span class="t-line" />
                </div>
              </div>

              <!-- 意图/来源标签：回答定型后展示 -->
              <span v-if="msg.role === 'assistant' && routeTag && !msg.streaming" class="x-tag mint prov">
                {{ routeTag }}{{ msg.knowledge_id ? ` · ${kbName(msg.knowledge_id) || msg.knowledge_id}` : '' }}
              </span>

              <div v-if="msg.content" class="content-text">
                {{ msg.content }}<span v-if="msg.streaming" class="caret" />
              </div>
              <div v-else-if="msg.streaming && !msg.thinking.length" class="thinking">正在思考…</div>

              <div v-if="msg.error" class="msg-err">{{ msg.error }} <button class="retry" @click="retryLast(idx)">重试</button></div>

              <!-- 引用来源 -->
              <div v-if="msg.role === 'assistant' && msg.sources?.length && !msg.streaming" class="refs">
                <div class="r-title">引用来源（{{ msg.sources.length }}）</div>
                <div v-for="(s, si) in msg.sources" :key="si" class="ref">
                  <span class="r-dot" />
                  <span class="r-name">{{ s.doc_name || s.doc_name || s.doc_id || ('片段 ' + (si + 1)) }}</span>
                </div>
              </div>
            </div>
            <div v-if="msg.role === 'assistant' && !msg.streaming && msg.content" class="msg-actions">
              <button class="act" @click="copyMessage(msg)"><XIcon name="copyDocument" :size="12" />复制回答</button>
            </div>
          </div>
        </div>

        <div v-if="showJumpBtn" class="jump-latest" @click="jumpLatest">
          <XIcon name="arrowDown" :size="12" /> 跳至最新
        </div>
      </div>

      <div class="chat-input">
        <div class="ci-box">
          <div class="x-input ci-input" style="height: auto; padding: 10px 14px; border: none; background: transparent; box-shadow: none">
            <textarea
              v-model="inputText" rows="1" style="resize: none"
              placeholder="输入你的问题，按 Enter 发送，Shift + Enter 换行…"
              @compositionstart="onCompositionStart"
              @compositionend="onCompositionEnd"
              @keydown.enter.exact.prevent="onEnter"
            />
            <span v-if="chat.sending" class="suffix" style="align-self: flex-end" @click="handleStop">停止</span>
          </div>
          <div class="ci-foot">
            <span class="ci-chip">引用折叠：跟随设置</span>
            <span class="ci-chip">挂载知识库</span>
            <select v-model="knowledgeId" class="kb-select" :disabled="chat.sending">
              <option value="">不挂载（自由对话）</option>
              <option v-for="kb in kbList" :key="kb.id" :value="String(kb.id)">{{ kb.name }}（{{ kb.docCount || 0 }} 文档）</option>
            </select>
            <button v-if="!chat.sending" class="ci-send" @click="handleSend">发送 <span class="sd" /></button>
            <button v-else class="ci-send stop" @click="handleStop"><XIcon name="videoPause" :size="13" />停止</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page { display: grid; grid-template-columns: 240px 1fr; height: 100vh; }

.chat-side {
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, var(--side1), var(--side2));
  padding: 16px 12px;
  display: flex; flex-direction: column; gap: 10px;
}
.new-chat { width: 100%; }
.side-title { font-size: 11px; color: var(--muted); letter-spacing: 2px; padding: 4px 10px; }
.session-list { display: flex; flex-direction: column; gap: 2px; overflow-y: auto; }
.session { position: relative; padding: 9px 10px 9px 24px; border-radius: 10px; cursor: pointer; font-size: 13px; }
.session::before { content: ""; position: absolute; left: 10px; top: 0; bottom: 0; width: 1.5px; background: var(--mint-deep); }
.session::after { content: ""; position: absolute; left: 6.5px; top: 15px; width: 8px; height: 8px; border-radius: 50%; background: var(--surface); border: 2px solid var(--dot); }
.session.active { background: var(--surface); box-shadow: 0 2px 10px color-mix(in srgb, var(--green) 10%, transparent); }
.session.active::after { border-color: var(--green); background: var(--bright); }
.session b { display: block; font-weight: 600; color: var(--ink-soft); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session.active b { color: var(--green-dark); }
.s-sub { font-size: 11px; color: var(--muted); }
.panel-tip { font-size: 12px; color: var(--muted); text-align: center; padding: 16px 8px; line-height: 1.9; }

.chat-main { display: flex; flex-direction: column; min-width: 0; }
.chat-head { display: flex; align-items: center; gap: 10px; padding: 14px 24px; border-bottom: 1px dashed var(--mint-deep); }
.hd-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--green); }
.kb-tag { margin-left: auto; }

.chat-body { flex: 1; overflow-y: auto; padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; position: relative; }
.chips { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; margin-top: 16px; }
.chip {
  font-size: 12px; color: var(--ink-soft); background: var(--surface);
  border: 1px solid var(--line); border-radius: 999px; padding: 0 14px; min-height: 32px;
  display: inline-flex; align-items: center; gap: 7px; cursor: pointer;
}
.chip::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: var(--blue); }
.chip:hover { border-color: var(--green); color: var(--green-dark); }

.msg { display: flex; gap: 10px; max-width: 86%; }
.msg.user { align-self: flex-end; flex-direction: row-reverse; }
.m-dot { width: 30px; height: 30px; border-radius: 50%; flex: none; position: relative; margin-top: 2px; }
.m-dot.user { background: var(--blue); }
.m-dot.user::after { content: ""; position: absolute; inset: 9px; border-radius: 50%; background: #fff; opacity: .9; }
.m-dot.assistant { background: var(--surface); border: 2px solid var(--green); }
.m-dot.assistant::after { content: ""; position: absolute; inset: 7px; border-radius: 50%; background: var(--bright); }

.msg-main { min-width: 0; }
.bubble {
  border-radius: 14px; padding: 11px 15px; font-size: 13.5px; line-height: 1.85;
  position: relative; word-break: break-word; corner-shape: squircle;
}
.bubble.user { background: var(--blue); color: #fff; border-top-right-radius: 4px; }
.bubble.assistant { background: var(--soft); border: 1px solid var(--line); border-top-left-radius: 4px; }
.content-text { white-space: pre-wrap; }
.caret { display: inline-block; width: 2px; height: 14px; background: var(--bright); vertical-align: -2px; margin-left: 2px; }
.thinking { color: var(--muted); font-size: 13px; }

.prov { margin-bottom: 8px; }

/* 检索过程时间线 */
.trace { margin: 2px 0 8px; }
.t-step { display: flex; align-items: center; gap: 9px; position: relative; padding: 4px 0; font-size: 12px; }
.t-step::before { content: ""; position: absolute; left: 5px; top: -5px; bottom: -5px; width: 1.5px; background: var(--mint-deep); }
.t-step:first-child::before { top: 12px; }
.t-step:last-child::before { bottom: 12px; }
.t-dot { width: 11px; height: 11px; border-radius: 50%; flex: none; position: relative; z-index: 1; }
.t-dot.done { background: var(--green); }
.t-dot.run { background: var(--blue); box-shadow: 0 0 0 4px color-mix(in srgb, var(--blue) 15%, transparent); }
.t-name { color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.t-line { flex: 1; height: 1px; background: repeating-linear-gradient(90deg, var(--line) 0 4px, transparent 4px 8px); }

.msg-err { margin-top: 8px; font-size: 12px; color: #C0453B; display: flex; align-items: center; gap: 8px; }
.retry { color: var(--green-dark); font-weight: 600; font-size: 12px; text-decoration: underline; }

.refs { margin-top: 10px; border-top: 1px dashed var(--mint-deep); padding-top: 9px; }
.r-title { font-size: 11px; color: var(--muted); margin-bottom: 6px; display: flex; align-items: center; gap: 8px; }
.r-title::after { content: ""; flex: 1; height: 1px; background: repeating-linear-gradient(90deg, var(--line) 0 4px, transparent 4px 8px); }
.ref { display: flex; align-items: center; gap: 7px; font-size: 12px; color: var(--ink-soft); padding: 2px 0; }
.r-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--green); flex: none; }
.ref:nth-child(3) .r-dot { background: var(--blue); }
.ref:nth-child(4) .r-dot { background: var(--bright); }
.r-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.msg-actions { display: flex; gap: 8px; margin-top: 6px; }
.act {
  display: inline-flex; align-items: center; gap: 5px; font-size: 11px;
  color: var(--muted); background: var(--surface); border-radius: 999px; padding: 4px 11px;
  border: 1px solid var(--line); min-height: 26px;
}
.act:hover { color: var(--green-dark); border-color: var(--green); }

.jump-latest {
  position: absolute; right: 18px; bottom: 8px;
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 11px; color: var(--muted); background: var(--surface);
  border-radius: 999px; padding: 5px 12px; cursor: pointer;
  box-shadow: 0 4px 14px color-mix(in srgb, var(--green) 14%, transparent);
}

.chat-input { margin: 0 24px 20px; }
.ci-box {
  border: 1.5px solid var(--line); border-radius: 16px; background: var(--surface);
  padding: 12px 16px; corner-shape: squircle;
  box-shadow: 0 6px 20px color-mix(in srgb, var(--green) 8%, transparent);
}
.ci-foot { display: flex; align-items: center; gap: 12px; margin-top: 10px; }
.ci-chip { font-size: 11px; color: var(--muted); border: 1px solid var(--line); border-radius: 999px; padding: 3px 10px; display: inline-flex; align-items: center; gap: 6px; }
.ci-chip::before { content: ""; width: 5px; height: 5px; border-radius: 50%; background: var(--blue); }
.kb-select {
  font-size: 12px; color: var(--ink-soft); background: var(--soft);
  border: 1px solid var(--line); border-radius: 999px; padding: 5px 10px; max-width: 200px;
}
.ci-send {
  margin-left: auto; display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  min-width: 84px; height: 34px; border-radius: 999px; background: var(--green); color: #fff;
  font-size: 13px; font-weight: 600;
}
.ci-send::after { content: ""; width: 5px; height: 5px; border-radius: 50%; background: var(--bright); }
.ci-send:hover { background: var(--green-dark); }
.ci-send.stop { background: var(--surface); color: #C0453B; border: 1.5px solid #EBC9C5; }
.ci-send.stop::after { display: none; }
.ci-send:active:not(:disabled) { transform: translateY(1px); }
.ci-send:disabled { opacity: .45; cursor: not-allowed; }
</style>
