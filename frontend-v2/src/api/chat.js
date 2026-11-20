import request from './request'

export const listSessions = () => request.get('/chat/sessions')
export const getSessionHistory = (sessionId) => request.get(`/chat/sessions/${sessionId}/history`)
export const deleteSession = (sessionId) => request.delete(`/chat/sessions/${sessionId}`)
export const renameSession = (sessionId, title) => request.put(`/chat/sessions/${sessionId}/title`, { title })

/**
 * SSE 流式对话：POST /api/chat/stream（不走 axios）。
 * 事件按 \n\n 分隔，data 为 JSON：type = token | thinking | done | error。
 * onEvent(payload) 由调用方实现；AbortSignal 支持停止生成（已生成内容保留）。
 */
export async function streamChat({ sessionId, query, knowledgeId, token, signal, onEvent }) {
  const body = { session_id: sessionId, query }
  if (knowledgeId) body.knowledge_id = knowledgeId
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })
  if (!resp.ok || !resp.body) throw new Error(`对话请求失败: ${resp.status}`)

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const event = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      const dataLine = event.split('\n').find((l) => l.startsWith('data:'))
      if (!dataLine) continue
      onEvent(JSON.parse(dataLine.slice(5).trim()))
    }
  }
}
