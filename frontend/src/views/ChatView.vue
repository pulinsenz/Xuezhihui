<template>
  <div class="chat-page">
    <!-- 顶部：标题 + 知识库选择 -->
    <div class="chat-header">
      <h2>AI 对话</h2>
      <el-select
        v-model="knowledgeId"
        placeholder="选择知识库（可选，不选则普通问答）"
        clearable
        filterable
        class="kb-select"
        :loading="kbLoading"
      >
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
    </div>

    <!-- 消息区 -->
    <div ref="messageAreaRef" class="message-area">
      <el-empty v-if="messages.length === 0" description="你好，我是学智汇 AI 助手，有问题尽管问～" />

      <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
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
        :disabled="sending"
        @keydown.enter.exact.prevent="handleSend"
      />
      <el-button type="primary" :icon="Promotion" :loading="sending" @click="handleSend">
        {{ sending ? '回答中' : '发送' }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { listMyKnowledge } from '../api/knowledge'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()

// 会话 id：本地生成并持久化，跨刷新延续（Python 侧存会话记忆）
const sessionId = ref(localStorage.getItem('chat_session_id') || crypto.randomUUID())
localStorage.setItem('chat_session_id', sessionId.value)

const messages = ref([])
const inputText = ref('')
const sending = ref(false)
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
  kbLoading.value = true
  try {
    kbList.value = await listMyKnowledge()
  } finally {
    kbLoading.value = false
  }
}

const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  inputText.value = ''

  // 追加用户消息
  messages.value.push({ role: 'user', content: text })
  // 追加 assistant 占位（流式追加）
  messages.value.push({ role: 'assistant', content: '', streaming: true })
  sending.value = true
  scrollToBottom()

  const curMsg = messages.value[messages.value.length - 1]
  // 构建 SSE URL（token 走 header，不放进 URL，避免泄露）
  const params = new URLSearchParams({ session_id: sessionId.value, query: text })
  if (knowledgeId.value) params.set('knowledge_id', knowledgeId.value)

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
          curMsg.content += payload.content
          scrollToBottom()
        } else if (payload.type === 'done') {
          if (!curMsg.content) curMsg.content = payload.answer || ''
          curMsg.streaming = false
          scrollToBottom()
        } else if (payload.type === 'error') {
          throw new Error(payload.message || '对话出错')
        }
      }
    }
    curMsg.streaming = false
    if (!curMsg.content) {
      curMsg.content = '（无回答）'
    }
  } catch (e) {
    curMsg.streaming = false
    curMsg.content = curMsg.content || `⚠️ ${e.message}`
    ElMessage.error(e.message || '对话失败，请稍后再试')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

onMounted(() => {
  loadKnowledge()
  scrollToBottom()
})
</script>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  padding: 0 20px;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid var(--el-border-color-light);
}
.chat-header h2 {
  font-size: 18px;
}
.kb-select {
  width: 280px;
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
