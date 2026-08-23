import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from '../chat'
import * as chatApi from '../../api/chat'

vi.mock('../../api/chat', () => ({
  listSessions: vi.fn(),
  getSessionHistory: vi.fn(),
  deleteSession: vi.fn(),
}))

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('loadSessions 拉取会话列表', async () => {
    chatApi.listSessions.mockResolvedValue([
      { session_id: 's1', title: '你好', update_time: 1, message_count: 2 },
    ])
    const store = useChatStore()
    await store.loadSessions()
    expect(store.sessions).toHaveLength(1)
    expect(store.sessions[0].session_id).toBe('s1')
  })

  it('openSession 加载历史并记录当前会话', async () => {
    chatApi.getSessionHistory.mockResolvedValue([{ role: 'user', content: 'hi' }])
    const store = useChatStore()
    await store.openSession('s1')
    expect(store.activeSessionId).toBe('s1')
    expect(store.messages).toHaveLength(1)
    expect(localStorage.getItem('chat_session_id')).toBe('s1')
  })

  it('openSession 加载失败保留空会话', async () => {
    chatApi.getSessionHistory.mockRejectedValue(new Error('x'))
    const store = useChatStore()
    await store.openSession('s1')
    expect(store.activeSessionId).toBe('s1')
    expect(store.messages).toEqual([])
  })

  it('sending 中 openSession 不切换（防 token 串扰）', async () => {
    const store = useChatStore()
    store.sending = true
    await store.openSession('s2')
    expect(store.activeSessionId).toBeNull()
  })

  it('newSession 生成新 id 并清空消息', () => {
    const store = useChatStore()
    store.messages = [{ role: 'user', content: 'x' }]
    store.newSession()
    expect(store.activeSessionId).toBeTruthy()
    expect(store.messages).toEqual([])
    expect(localStorage.getItem('chat_session_id')).toBe(store.activeSessionId)
  })

  it('deleteSession 删除当前会话后跳到最近剩余会话', async () => {
    chatApi.getSessionHistory.mockResolvedValue([])
    const store = useChatStore()
    store.sessions = [
      { session_id: 's1', title: 'a' },
      { session_id: 's2', title: 'b' },
    ]
    store.activeSessionId = 's1'
    await store.deleteSession('s1')
    expect(chatApi.deleteSession).toHaveBeenCalledWith('s1')
    expect(store.sessions).toHaveLength(1)
    expect(store.activeSessionId).toBe('s2')
  })

  it('deleteSession 删空后新建会话', async () => {
    const store = useChatStore()
    store.sessions = [{ session_id: 's1', title: 'a' }]
    store.activeSessionId = 's1'
    await store.deleteSession('s1')
    expect(store.sessions).toEqual([])
    expect(store.activeSessionId).toBeTruthy()
  })

  it('resetForGuest 清空并重置本地会话 id', () => {
    const store = useChatStore()
    store.sessions = [{ session_id: 's1' }]
    store.activeSessionId = 's1'
    store.messages = [{ role: 'user', content: 'x' }]
    store.resetForGuest()
    expect(store.sessions).toEqual([])
    expect(store.activeSessionId).toBeNull()
    expect(store.messages).toEqual([])
    expect(localStorage.getItem('chat_session_id')).toBeTruthy()
  })

  it('流式辅助正确追加与收尾', async () => {
    chatApi.listSessions.mockResolvedValue([])
    const store = useChatStore()
    store.pushUserMessage('你好')
    store.pushAssistantPlaceholder()
    expect(store.messages).toHaveLength(2)
    expect(store.messages[1].streaming).toBe(true)
    store.appendToken('你')
    store.appendToken('好')
    await store.finishStream('')
    expect(store.messages[1].content).toBe('你好')
    expect(store.messages[1].streaming).toBe(false)
  })

  it('failStream 显示错误占位并结束发送态', () => {
    const store = useChatStore()
    store.sending = true
    store.pushAssistantPlaceholder()
    store.failStream('出错了')
    expect(store.messages[0].content).toContain('出错了')
    expect(store.sending).toBe(false)
  })
})
