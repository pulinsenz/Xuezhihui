import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearInputDrafts, downloadConversation, formatConversation, readInputDraft, visibleSessions, writeInputDraft } from '../chatConvenience'

beforeEach(() => localStorage.clear())

describe('输入草稿', () => {
  it('按账号、会话隔离并可删除', () => {
    writeInputDraft('u1', 's1', '未发的问题')
    writeInputDraft('u1', 's2', '另一会话')
    writeInputDraft('u2', 's1', '另一用户')
    expect(readInputDraft('u1', 's1')).toBe('未发的问题')
    expect(readInputDraft('u1', 's2')).toBe('另一会话')
    expect(readInputDraft('u2', 's1')).toBe('另一用户')
    writeInputDraft('u1', 's1', '')
    expect(readInputDraft('u1', 's1')).toBe('')
    expect(readInputDraft('u2', 's1')).toBe('另一用户')
  })
  it('退出只清除当前账号草稿，避免前缀相似的账号误清', () => {
    writeInputDraft('1', 's', 'a')
    writeInputDraft('11', 's', 'b')
    clearInputDrafts('1')
    expect(readInputDraft('1', 's')).toBe('')
    expect(readInputDraft('11', 's')).toBe('b')
  })
  it('访客不写入草稿，存储失败不抛错', () => {
    expect(writeInputDraft(null, 's', 'a')).toBe(false)
    const storage = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('QuotaExceeded') })
    expect(writeInputDraft('u', 's', 'a')).toBe(false)
    storage.mockRestore()
  })
})

describe('会话搜索排序', () => {
  const sessions = [
    { title: 'HTTP 缓存', update_time: '300' },
    { title: '课程', pinned: true, update_time: '100' },
    { title: 'http 协议', pinned: true, update_time: '200' },
  ]
  it('大小写不敏感，置顶优先后按活跃时间排序，保留原数组', () => {
    expect(visibleSessions(sessions, ' HTTP ').map((s) => s.title)).toEqual(['http 协议', 'HTTP 缓存'])
    expect(visibleSessions(sessions)[1].title).toBe('课程')
    expect(sessions[0].title).toBe('HTTP 缓存')
    expect(visibleSessions(sessions, '不存在')).toEqual([])
  })
})

describe('导出', () => {
  const messages = [
    { role: 'user', content: '什么是缓存？' },
    { role: 'assistant', content: '**回答**[3]', sources: [{ cite_no: 3, doc_name: '课程.md', text: '完整来源' }] },
    { role: 'assistant', content: '部分', stopped: true },
  ]
  it('Markdown 保留问答、引用编号和停止标记，TXT 有清楚的角色标识', () => {
    const md = formatConversation(messages, '缓存学习')
    expect(md).toContain('# 缓存学习')
    expect(md).toContain('## 用户\n\n什么是缓存？')
    expect(md).toContain('[3] 课程.md\n完整来源')
    expect(md).toContain('已停止生成')
    const txt = formatConversation(messages, '缓存学习', 'txt')
    expect(txt).toContain('【AI 助手】')
    expect(txt).not.toContain('## 用户')
  })
  it('触发文件下载，清理文件名与对象 URL', () => {
    vi.useFakeTimers()
    const create = vi.fn(() => 'blob:export')
    const revoke = vi.fn()
    vi.stubGlobal('URL', { createObjectURL: create, revokeObjectURL: revoke })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
      expect(this.download).toBe('课程_复习.md')
      expect(this.href).toBe('blob:export')
    })
    downloadConversation(messages, '课程/复习', 'md')
    expect(click).toHaveBeenCalledOnce()
    expect(create.mock.calls[0][0]).toBeInstanceOf(Blob)
    expect(document.querySelector('a')).toBeNull()
    vi.runAllTimers()
    expect(revoke).toHaveBeenCalledWith('blob:export')
    click.mockRestore()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })
})
