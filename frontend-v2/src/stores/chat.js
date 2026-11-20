import { defineStore, acceptHMRUpdate } from 'pinia'
import { listSessions, getSessionHistory, deleteSession, renameSession as renameSessionApi } from '../api/chat'

/** 会话 ID：UUID v4（HTTP 非安全上下文降级 getRandomValues / Math.random） */
function generateSessionId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = crypto.getRandomValues(new Uint8Array(16))
    bytes[6] = (bytes[6] & 0x0f) | 0x40
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('')
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

/**
 * 对话状态：会话列表 + 当前会话消息 + 流式状态。
 * 历史持久化在 MySQL（Java 侧），本 store 只做展示与交互编排（约定同老前端）。
 * 消息字段：role / content / streaming / thinking[] / route / sources[] / knowledge_id / error / stopped
 */
export const useChatStore = defineStore('chat', {
  state: () => ({
    sessions: [],
    activeSessionId: null,
    messages: [],
    sending: false,
  }),
  actions: {
    async loadSessions() {
      this.sessions = await listSessions()
    },
    async openSession(id) {
      this.activeSessionId = id
      const history = await getSessionHistory(id)
      this.messages = history.messages || []
      // 历史消息可能带 sources，按历史 enrich 逻辑由页面处理文档名
    },
    newSession() {
      this.activeSessionId = generateSessionId()
      this.messages = []
    },
    async removeSession(id) {
      await deleteSession(id)
      this.sessions = this.sessions.filter((s) => s.session_id !== id)
      if (this.activeSessionId === id) {
        this.activeSessionId = null
        this.messages = []
      }
    },
    async renameSession(id, title) {
      await renameSessionApi(id, title)
      const s = this.sessions.find((x) => x.session_id === id)
      if (s) s.title = title
    },
    resetForGuest() {
      this.sessions = []
      this.activeSessionId = null
      this.messages = []
      this.sending = false
    },
    // ---- 流式辅助（ChatView 的 SSE 循环调用） ----
    pushUserMessage(text) {
      this.messages.push({ role: 'user', content: text })
    },
    pushAssistantPlaceholder() {
      this.messages.push({ role: 'assistant', content: '', streaming: true, thinking: [], route: '', sources: [] })
    },
    appendToken(text) {
      const m = this.messages[this.messages.length - 1]
      if (m?.role === 'assistant') m.content += text
    },
    addThinking(text) {
      const m = this.messages[this.messages.length - 1]
      if (m?.role === 'assistant') m.thinking.push(text)
    },
    async finishStream({ answer = '', route = '', sources = [], knowledge_id = '', session_id = '' } = {}) {
      const m = this.messages[this.messages.length - 1]
      if (!m || m.role !== 'assistant') return
      if (answer) m.content = answer
      m.route = route
      m.sources = sources
      m.knowledge_id = knowledge_id
      m.streaming = false
      if (session_id && !this.sessions.some((s) => s.session_id === session_id)) {
        // 新会话首问完成后刷新会话列表（标题/时间由后端生成）
        this.loadSessions().catch(() => {})
        this.activeSessionId = session_id
      }
    },
    // stopped=true 表示用户主动停止（不算失败，不显示重试）；失败轮次后端未落库，本地可安全重试
    failStream(msg, stopped = false) {
      const m = this.messages[this.messages.length - 1]
      if (!m || m.role !== 'assistant') return
      m.streaming = false
      m.error = stopped ? '' : msg || '对话失败'
      m.stopped = stopped
      if (stopped && !m.content) m.content = '已停止生成'
    },
  },
})

if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useChatStore, import.meta.hot))
}
