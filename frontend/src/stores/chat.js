import { defineStore } from 'pinia'
import { listSessions, getSessionHistory, deleteSession } from '../api/chat'

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

    newSession() {
      const id = crypto.randomUUID()
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

    resetForGuest() {
      this.sessions = []
      this.activeSessionId = null
      this.messages = []
      this.sending = false
      // 访客会话独立，避免复用他人/上次登录的会话 id
      localStorage.setItem('chat_session_id', crypto.randomUUID())
    },

    // ---- 流式辅助（由 ChatView 的 SSE 循环调用）----
    pushUserMessage(text) {
      this.messages.push({ role: 'user', content: text })
    },
    pushAssistantPlaceholder() {
      this.messages.push({ role: 'assistant', content: '', streaming: true })
    },
    appendToken(text) {
      const cur = this.messages[this.messages.length - 1]
      if (cur) cur.content += text
    },
    async finishStream(answer) {
      const cur = this.messages[this.messages.length - 1]
      if (cur) {
        cur.streaming = false
        if (!cur.content) cur.content = answer || '（无回答）'
      }
      this.sending = false
      // 刷新面板标题/条数/排序
      try {
        await this.loadSessions()
      } catch (e) {
        // 忽略：列表刷新失败不影响当前对话
      }
    },
    failStream(msg) {
      const cur = this.messages[this.messages.length - 1]
      if (cur) {
        cur.streaming = false
        cur.content = cur.content || `⚠️ ${msg}`
      }
      this.sending = false
    },
  },
})
