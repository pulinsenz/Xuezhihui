import { defineStore, acceptHMRUpdate } from 'pinia'
import { listSessions, getSessionHistory, deleteSession, renameSession as renameSessionApi, pinSession as pinSessionApi, rateMessage as rateMessageApi } from '../api/chat'

/**
 * 生成会话 ID（UUID v4）。
 * 优先用 crypto.randomUUID；HTTP 生产环境（非安全上下文）下该方法不可用，
 * 降级到同样可在 HTTP 下使用的 crypto.getRandomValues，最后兜底 Math.random。
 */
function generateSessionId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = crypto.getRandomValues(new Uint8Array(16))
    bytes[6] = (bytes[6] & 0x0f) | 0x40 // version 4
    bytes[8] = (bytes[8] & 0x3f) | 0x80 // variant 10
    const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('')
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/**
 * 对话状态：会话列表 + 当前会话消息 + 流式发送状态。
 * 历史持久化在 MySQL（Java 侧），本 store 只做展示与交互编排。
 */
export const useChatStore = defineStore('chat', {
  state: () => ({
    sessions: [], // 会话列表（最新在前）
    activeSessionId: null,
    messages: [], // 当前会话消息
    sending: false, // 流式回复中，禁用切换/新建/删除
  }),
  actions: {
    async pinSession(id, pinned) {
      await pinSessionApi(id, pinned)
      const session = this.sessions.find((s) => s.session_id === id)
      if (session) session.pinned = pinned
    },

    async syncMessageIds() {
      const sessionId = this.activeSessionId
      const history = await getSessionHistory(sessionId)
      if (this.activeSessionId !== sessionId) return
      // 中断轮次可能没落库，也可能保存了部分回答；先完整核对再赋 ID，不能仅凭数组位置评价。
      const local = this.messages
      const isTurn = (list, i) => list[i]?.role === 'user' && list[i + 1]?.role === 'assistant'
      if (local.length % 2 || history.length % 2) return
      let successful = 0
      for (let i = 0; i < local.length; i += 2) {
        if (!isTurn(local, i)) return
        if (!local[i + 1].failed && !local[i + 1].stopped) successful++
      }
      let interrupted = history.length / 2 - successful
      if (interrupted < 0) return
      const matched = []
      let cursor = 0
      for (let i = 0; i < local.length; i += 2) {
        const answer = local[i + 1]
        const sameQuestion = isTurn(history, cursor) && local[i].content === history[cursor].content
        if (answer.failed || answer.stopped) {
          if (interrupted > 0 && sameQuestion && history[cursor + 1].content.startsWith(answer.content || '')) {
            interrupted--
            cursor += 2
          }
          continue
        }
        if (!sameQuestion || answer.content !== history[cursor + 1].content) return
        for (let offset = 0; offset < 2; offset++) {
          if (local[i + offset].id && local[i + offset].id !== history[cursor + offset].id) return
          matched.push([local[i + offset], history[cursor + offset]])
        }
        cursor += 2
      }
      if (cursor !== history.length || interrupted) return
      matched.forEach(([message, saved]) => {
        message.id = saved.id
        message.feedback = saved.feedback ?? 0
      })
    },

    async rateMessage(message, feedback) {
      const sessionId = this.activeSessionId
      if (!message.id) await this.syncMessageIds()
      if (!message.id || this.activeSessionId !== sessionId) throw new Error('回答尚未保存，请稍后重试')
      await rateMessageApi(sessionId, message.id, feedback)
      message.feedback = feedback
    },

    async loadSessions() {
      this.sessions = await listSessions()
    },

    async openSession(id) {
      if (this.sending) return
      this.activeSessionId = id
      localStorage.setItem('chat_session_id', id)
      try {
        this.messages = await getSessionHistory(id)
      } catch (e) {
        // 加载失败（如临时网络错误），错误已由拦截器提示，保留空会话
        this.messages = []
      }
    },

    newSession(id = generateSessionId()) {
      this.activeSessionId = id
      this.messages = []
      localStorage.setItem('chat_session_id', id)
    },

    async deleteSession(id) {
      await deleteSession(id)
      this.sessions = this.sessions.filter((s) => s.session_id !== id)
      // 删除的是当前会话 → 跳到最近剩余会话，无则新建
      if (id === this.activeSessionId) {
        const next = this.sessions[0]
        if (next) await this.openSession(next.session_id)
        else this.newSession()
      }
    },

    async renameSession(id, title) {
      await renameSessionApi(id, title)
      // 仅本地改标题：重命名不改活跃时间，列表顺序与 message_count 均不变
      const s = this.sessions.find((item) => item.session_id === id)
      if (s) s.title = title
    },

    resetForGuest() {
      this.sessions = []
      this.activeSessionId = null
      this.messages = []
      this.sending = false
      // 访客会话独立，避免复用他人/上次登录的会话 id
      localStorage.setItem('chat_session_id', generateSessionId())
    },

    // ---- 流式辅助（由 ChatView 的 SSE 循环调用）----
    pushUserMessage(text) {
      this.messages.push({ role: 'user', content: text })
    },
    pushAssistantPlaceholder() {
      this.messages.push({ role: 'assistant', content: '', streaming: true, thinking: [], route: '', sources: [] })
    },
    appendToken(text) {
      const cur = this.messages[this.messages.length - 1]
      if (cur) cur.content += text
    },
    addThinking(text) {
      const cur = this.messages[this.messages.length - 1]
      if (cur && cur.thinking) cur.thinking.push(text)
    },
    async finishStream({ answer = '', route = '', sources = [], knowledge_id = '' } = {}, keepSending = false) {
      const cur = this.messages[this.messages.length - 1]
      if (cur) {
        cur.streaming = false
        if (!cur.content) cur.content = answer || '（无回答）'
        cur.route = route
        cur.sources = sources || []
        cur.knowledge_id = knowledge_id
      }
      this.sending = keepSending
      // 刷新面板标题/条数/排序
      try {
        await this.loadSessions()
      } catch (e) {
        // 忽略：列表刷新失败不影响当前对话
      }
    },
    // stopped=true 表示用户主动停止（不算失败，不显示重试）；失败轮次后端未落库，本地可安全重试
    failStream(msg, stopped = false) {
      const cur = this.messages[this.messages.length - 1]
      if (cur) {
        cur.streaming = false
        cur.errorText = msg // 气泡内尾注：content 为空时是唯一内容，有部分内容时作补充
        cur.failed = !stopped
        if (stopped) cur.stopped = true
      }
      this.sending = false
    },
  },
})

// 支持 HMR：vite 热更新 store 时正确替换定义，避免运行中的 store 持有旧 action
if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useChatStore, import.meta.hot))
}
