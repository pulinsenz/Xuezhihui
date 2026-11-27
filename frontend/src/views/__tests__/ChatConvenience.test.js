import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ChatView from '../ChatView.vue'
import { useAuthStore } from '../../stores/auth'
import { useChatStore } from '../../stores/chat'
import * as chatApi from '../../api/chat'
import * as knowledgeApi from '../../api/knowledge'
import { readInputDraft, writeInputDraft } from '../../utils/chatConvenience'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: {} }), useRouter: () => ({ replace: vi.fn() }) }))
vi.mock('../../api/chat', () => ({ listSessions: vi.fn(), getSessionHistory: vi.fn(), deleteSession: vi.fn(), renameSession: vi.fn(), pinSession: vi.fn(), rateMessage: vi.fn() }))
vi.mock('../../api/knowledge', () => ({ listMyKnowledge: vi.fn(), listDocs: vi.fn(), listKnowledgeQuestions: vi.fn() }))
vi.mock('../../api/settings', () => ({ getSettings: vi.fn(async () => ({})) }))

const inputStub = { props: ['modelValue'], emits: ['update:modelValue'], template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }
const buttonStub = { template: '<button><slot /></button>' }
let wrapper
let chat
let auth

const mount = async () => {
  wrapper = shallowMount(ChatView, {
    global: { renderStubDefaultSlot: true, stubs: { ElInput: inputStub, ElButton: buttonStub } },
  })
  await flushPromises()
  return wrapper
}
const input = () => wrapper.find('textarea[aria-label="问题内容"]')

beforeEach(() => {
  localStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
  auth = useAuthStore()
  auth.token = 'test-token'
  auth.user = { id: 'u1' }
  chat = useChatStore()
  chatApi.listSessions.mockResolvedValue([{ session_id: 's1', title: '课程' }, { session_id: 's2', title: '作业' }])
  chatApi.getSessionHistory.mockResolvedValue([])
  knowledgeApi.listMyKnowledge.mockResolvedValue([{ id: 'k1', name: '课程库' }])
  knowledgeApi.listKnowledgeQuestions.mockResolvedValue(['如何考核？'])
  localStorage.setItem('chat_session_id', 's1')
})
afterEach(() => { wrapper?.unmount(); vi.unstubAllGlobals() })

describe('对话界面功能', () => {
  it('输入即时持久化，切换和重新挂载恢复各自草稿，退出清理', async () => {
    await mount()
    await input().setValue('课程问题')
    expect(readInputDraft('u1', 's1')).toBe('课程问题')
    await chat.openSession('s2')
    await flushPromises()
    expect(input().element.value).toBe('')
    await input().setValue('作业问题')
    wrapper.unmount()
    await mount()
    expect(input().element.value).toBe('作业问题')
    await chat.openSession('s1')
    await flushPromises()
    expect(input().element.value).toBe('课程问题')
    auth.clear()
    await flushPromises()
    expect(readInputDraft('u1', 's1')).toBe('')
    expect(readInputDraft('u1', 's2')).toBe('')
  })

  it('未落库的新会话刷新后也能恢复草稿', async () => {
    localStorage.setItem('chat_session_id', 'new-session')
    writeInputDraft('u1', 'new-session', '尚未发送')
    await mount()
    expect(chat.activeSessionId).toBe('new-session')
    expect(input().element.value).toBe('尚未发送')
  })

  it('失败保留问题，正常完成后清除草稿', async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new Error('offline'))
    vi.stubGlobal('fetch', fetch)
    await mount()
    await input().setValue('待发问题')
    await input().trigger('keydown.enter')
    await flushPromises()
    expect(input().element.value).toBe('待发问题')
    expect(readInputDraft('u1', 's1')).toBe('待发问题')

    let read = 0
    fetch.mockResolvedValueOnce({ ok: true, body: { getReader: () => ({ read: async () => read++ === 0
      ? { done: false, value: new TextEncoder().encode('data: {"type":"done","answer":"回答"}\n\n') }
      : { done: true } }) } })
    await input().trigger('keydown.enter')
    await flushPromises()
    expect(input().element.value).toBe('')
    expect(readInputDraft('u1', 's1')).toBe('')
  })

  it('搜索实时过滤列表，置顶后显示状态并排在前面', async () => {
    await mount()
    const search = wrapper.find('textarea[aria-label="搜索会话标题"]')
    await search.setValue('课程')
    expect(wrapper.findAll('.session-item')).toHaveLength(1)
    expect(wrapper.find('.session-item').text()).toContain('课程')
    await search.setValue('')
    await chat.pinSession('s2', true)
    await flushPromises()
    expect(wrapper.find('.session-item').text()).toContain('已置顶')
    expect(wrapper.find('.session-item').text()).toContain('作业')
  })

  it('会话面板可展开，打开历史会话后自动收起', async () => {
    await mount()
    const toggle = wrapper.get('.session-toggle')
    const panel = wrapper.get('#chat-session-panel')

    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(panel.classes()).toContain('is-open')

    await panel.findAll('.session-open')[1].trigger('click')
    await flushPromises()
    expect(chat.activeSessionId).toBe('s2')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(panel.classes()).not.toContain('is-open')
  })

  it('选择知识库后常用问题可一键携带知识库发起提问', async () => {
    const fetch = vi.fn().mockRejectedValue(new Error('offline'))
    vi.stubGlobal('fetch', fetch)
    await mount()
    wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'k1')
    await flushPromises()
    expect(wrapper.find('.suggest-chip').text()).toBe('如何考核？')
    await wrapper.find('.suggest-chip').trigger('click')
    await flushPromises()
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ session_id: 's1', knowledge_id: 'k1', query: '如何考核？' })
    expect(input().element.value).toBe('如何考核？')
  })
})
