const draftPrefix = 'xzh:chat-input:'
const draftKey = (uid, sid) => `${draftPrefix}${encodeURIComponent(uid)}:${encodeURIComponent(sid)}`

// 隐私模式/空间不足时允许继续输入，草稿失败不阻断对话。
export function readInputDraft(uid, sid) {
  if (!uid || !sid) return ''
  try { return localStorage.getItem(draftKey(uid, sid)) || '' } catch { return '' }
}

export function writeInputDraft(uid, sid, text) {
  if (!uid || !sid) return false
  try {
    if (text) localStorage.setItem(draftKey(uid, sid), text)
    else localStorage.removeItem(draftKey(uid, sid))
    return true
  } catch { return false }
}

export function clearInputDrafts(uid) {
  if (!uid) return
  try {
    const prefix = `${draftPrefix}${encodeURIComponent(uid)}:`
    Object.keys(localStorage).filter((key) => key.startsWith(prefix)).forEach((key) => localStorage.removeItem(key))
  } catch { /* 存储不可用时仍允许退出 */ }
}

export function visibleSessions(sessions, keyword = '') {
  const search = keyword.trim().toLocaleLowerCase()
  return sessions.filter((s) => (s.title || '未命名会话').toLocaleLowerCase().includes(search))
    .sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned) || Number(b.update_time || 0) - Number(a.update_time || 0))
}

export function formatConversation(messages, title, format = 'md') {
  const markdown = format === 'md'
  const lines = [markdown ? `# ${title}` : title, '']
  for (const message of messages) {
    if (!message.content && !message.errorText && !message.stopped) continue
    const role = message.role === 'user' ? '用户' : 'AI 助手'
    lines.push(markdown ? `## ${role}` : `【${role}】`, '', message.content || '', '')
    if (message.stopped || message.failed) lines.push(message.stopped ? '（已停止生成）' : `（回答失败：${message.errorText || '请重试'}）`, '')
    if (message.sources?.length) {
      lines.push(markdown ? '### 参考来源' : '参考来源', '')
      message.sources.forEach((source, index) => {
        lines.push(`[${source.cite_no ?? index + 1}] ${source.doc_name || source.doc_id || '知识库资料'}`, source.text || '', '')
      })
    }
  }
  return lines.join('\n')
}

export function downloadConversation(messages, title, format) {
  const content = formatConversation(messages, title, format)
  const blob = new Blob(['\uFEFF', content], { type: format === 'md' ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${title.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 64) || '对话记录'}.${format}`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
