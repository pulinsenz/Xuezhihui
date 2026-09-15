import request from './request'

/** 当前用户的会话列表（对话历史侧栏） */
export const listSessions = () => request.get('/chat/sessions')

/** 会话完整历史（仅本人） */
export const getSessionHistory = (sessionId) => request.get(`/chat/sessions/${sessionId}/history`)

/** 删除会话及其消息（仅本人） */
export const deleteSession = (sessionId) => request.delete(`/chat/sessions/${sessionId}`)
export const renameSession = (sessionId, title) => request.put(`/chat/sessions/${sessionId}/title`, { title })
