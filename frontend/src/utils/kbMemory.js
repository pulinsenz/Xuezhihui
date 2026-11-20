// 知识库选择记忆：会话级覆盖（选了但未发送）+ 空会话草稿（所有空对话共享一份）
// 全部存 localStorage，按 userId 隔离命名空间，避免多账号串选择
const keyOf = (uid, suffix) => `chat_kb_${suffix}_${uid}`

/** 空会话草稿：新开空对话默认显示的知识库；空会话发送定型后清空 */
export function readDraft(uid) {
  try {
    return localStorage.getItem(keyOf(uid, 'draft')) || ''
  } catch {
    return ''
  }
}

export function writeDraft(uid, value) {
  try {
    if (value) localStorage.setItem(keyOf(uid, 'draft'), String(value))
    else localStorage.removeItem(keyOf(uid, 'draft'))
  } catch {
    /* 隐私模式等场景 localStorage 不可写，静默降级为不记忆 */
  }
}

/** 会话级覆盖：某会话内改了知识库但尚未发送消息时的记忆 */
export function readSessionSel(uid, sessionId) {
  try {
    return localStorage.getItem(keyOf(uid, `sel_${sessionId}`)) || ''
  } catch {
    return ''
  }
}

export function writeSessionSel(uid, sessionId, value) {
  try {
    if (value) localStorage.setItem(keyOf(uid, `sel_${sessionId}`), String(value))
    else localStorage.removeItem(keyOf(uid, `sel_${sessionId}`))
  } catch {
    /* 同上 */
  }
}

/** 该会话最后使用的知识库：取最后一条 assistant 消息的 knowledge_id（'' 表示明确无库） */
export function lastUsedKbId(messages) {
  const list = messages || []
  for (let i = list.length - 1; i >= 0; i--) {
    const m = list[i]
    if (m?.role === 'assistant') return String(m.knowledge_id || '')
  }
  return ''
}

/** 知识库 id 是否有效；列表未就绪（空）时信任记忆值，由调用方在列表就绪后校正 */
export function kbIdInList(id, kbList) {
  if (!id) return false
  if (!kbList || !kbList.length) return true
  return kbList.some((kb) => String(kb.id) === String(id))
}

/**
 * 切回已有消息的会话时恢复知识库选择：
 * 本地覆盖（改了但未发送）优先；否则取该会话最后使用的库；失效记忆清除并回退。
 */
export function resolveSessionKb({ uid, sessionId, messages, kbList }) {
  const saved = readSessionSel(uid, sessionId)
  if (saved) {
    if (kbIdInList(saved, kbList)) return saved
    writeSessionSel(uid, sessionId, '') // 记忆的库已删除/无权限，清除失效覆盖
  }
  const last = lastUsedKbId(messages)
  return kbIdInList(last, kbList) ? last : ''
}

/** 新开/切到空会话时恢复知识库选择（所有空对话共享一份草稿）；失效草稿清除 */
export function resolveDraftKb({ uid, kbList }) {
  const draft = readDraft(uid)
  if (draft && !kbIdInList(draft, kbList)) writeDraft(uid, '')
  return kbIdInList(draft, kbList) ? draft : ''
}
