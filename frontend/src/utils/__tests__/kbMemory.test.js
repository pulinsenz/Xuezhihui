import { beforeEach, describe, expect, it } from 'vitest'
import {
  readDraft,
  writeDraft,
  readSessionSel,
  writeSessionSel,
  lastUsedKbId,
  kbIdInList,
  resolveSessionKb,
  resolveDraftKb,
} from '../kbMemory'

const KB_LIST = [
  { id: 1, name: '库a' },
  { id: 2, name: '库b' },
]

beforeEach(() => localStorage.clear())

describe('草稿（空会话共享记忆）', () => {
  it('写入后可读，按 userId 隔离', () => {
    writeDraft('u1', '2')
    writeDraft('u2', '1')
    expect(readDraft('u1')).toBe('2')
    expect(readDraft('u2')).toBe('1')
  })

  it('写空值即清除', () => {
    writeDraft('u1', '2')
    writeDraft('u1', '')
    expect(readDraft('u1')).toBe('')
    expect(localStorage.getItem('chat_kb_draft_u1')).toBeNull()
  })
})

describe('会话级覆盖（选了但未发送）', () => {
  it('按 sessionId 隔离，写空值即清除', () => {
    writeSessionSel('u1', 's1', '1')
    writeSessionSel('u1', 's2', '2')
    expect(readSessionSel('u1', 's1')).toBe('1')
    expect(readSessionSel('u1', 's2')).toBe('2')
    writeSessionSel('u1', 's1', '')
    expect(readSessionSel('u1', 's1')).toBe('')
    expect(readSessionSel('u1', 's2')).toBe('2')
  })
})

describe('lastUsedKbId', () => {
  it('取最后一条 assistant 消息所用库', () => {
    const msgs = [
      { role: 'user', content: 'q1' },
      { role: 'assistant', knowledge_id: '1' },
      { role: 'user', content: 'q2' },
      { role: 'assistant', knowledge_id: '' }, // 明确无库的一轮
    ]
    expect(lastUsedKbId(msgs)).toBe('')
  })

  it('无 assistant 消息返回空', () => {
    expect(lastUsedKbId([{ role: 'user', content: 'q' }])).toBe('')
    expect(lastUsedKbId([])).toBe('')
  })
})

describe('kbIdInList', () => {
  it('数字 id 与字符串记忆值归一化比较', () => {
    expect(kbIdInList('1', KB_LIST)).toBe(true)
    expect(kbIdInList(2, KB_LIST)).toBe(true)
    expect(kbIdInList('3', KB_LIST)).toBe(false)
    expect(kbIdInList('', KB_LIST)).toBe(false)
  })

  it('列表未就绪（空）时信任记忆值', () => {
    expect(kbIdInList('9', [])).toBe(true)
    expect(kbIdInList('9', null)).toBe(true)
  })
})

describe('resolveSessionKb（切回会话恢复）', () => {
  it('本地覆盖优先（改了但未发送的场景）', () => {
    writeSessionSel('u1', 's1', '2')
    const msgs = [
      { role: 'user' },
      { role: 'assistant', knowledge_id: '1' },
    ]
    expect(resolveSessionKb({ uid: 'u1', sessionId: 's1', messages: msgs, kbList: KB_LIST })).toBe('2')
  })

  it('无覆盖时取会话最后使用的库', () => {
    const msgs = [
      { role: 'assistant', knowledge_id: '1' },
      { role: 'user' },
      { role: 'assistant', knowledge_id: '2' },
    ]
    expect(resolveSessionKb({ uid: 'u1', sessionId: 's1', messages: msgs, kbList: KB_LIST })).toBe('2')
  })

  it('覆盖的库已失效时清除覆盖并回退到消息推导', () => {
    writeSessionSel('u1', 's1', '99')
    const msgs = [{ role: 'assistant', knowledge_id: '1' }]
    expect(resolveSessionKb({ uid: 'u1', sessionId: 's1', messages: msgs, kbList: KB_LIST })).toBe('1')
    expect(readSessionSel('u1', 's1')).toBe('')
  })

  it('明确无库的会话恢复为空，不会被更早用库的轮次污染', () => {
    const msgs = [
      { role: 'assistant', knowledge_id: '1' },
      { role: 'assistant', knowledge_id: '' },
    ]
    expect(resolveSessionKb({ uid: 'u1', sessionId: 's1', messages: msgs, kbList: KB_LIST })).toBe('')
  })
})

describe('resolveDraftKb（空会话恢复）', () => {
  it('返回草稿值', () => {
    writeDraft('u1', '2')
    expect(resolveDraftKb({ uid: 'u1', kbList: KB_LIST })).toBe('2')
  })

  it('无草稿返回空', () => {
    expect(resolveDraftKb({ uid: 'u1', kbList: KB_LIST })).toBe('')
  })

  it('草稿指向已删除的库时清除并返回空', () => {
    writeDraft('u1', '99')
    expect(resolveDraftKb({ uid: 'u1', kbList: KB_LIST })).toBe('')
    expect(readDraft('u1')).toBe('')
  })
})
