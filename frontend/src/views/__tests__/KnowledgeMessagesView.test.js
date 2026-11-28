import { afterEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import {
  acceptKnowledgeInvitation,
  addMember,
  listKnowledgeInvitations,
  readKnowledgeInvitation,
  rejectKnowledgeInvitation,
} from '../../api/knowledge'
import KnowledgeMessagesView from '../KnowledgeMessagesView.vue'

vi.mock('../../api/knowledge', () => ({
  acceptKnowledgeInvitation: vi.fn(),
  addMember: vi.fn(),
  listKnowledgeInvitations: vi.fn(),
  readKnowledgeInvitation: vi.fn(),
  rejectKnowledgeInvitation: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn() },
}))

const knowledgeId = '2126146296831619074'
const targetUserId = '2126146296831619075'
const invitation = {
  knowledgeId,
  targetUserId,
  knowledgeName: '课程资料',
  inviterName: '管理员',
  targetUserName: '管理员2',
}

let wrapper

afterEach(() => {
  wrapper?.unmount()
  localStorage.removeItem('token')
  vi.resetAllMocks()
})

async function mountMessages(rows) {
  localStorage.setItem('token', 'test-token')
  listKnowledgeInvitations.mockResolvedValue(rows)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/messages', component: KnowledgeMessagesView },
      { path: '/knowledge/:id', name: 'KnowledgeDetail', component: { template: '<div />' } },
    ],
  })
  await router.push('/messages')
  wrapper = mount(KnowledgeMessagesView, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  return router
}

function rowActions() {
  return wrapper.findAll('.invitation-row').map((row) => (
    row.findAll('.invitation-actions button, .invitation-actions a')
  ))
}

it('在各列表行显示与邀请方向和状态对应的操作', async () => {
  await mountMessages([
    { ...invitation, id: '1', direction: 'incoming', status: 'ACCEPTED', readTime: '2026-08-30' },
    { ...invitation, id: '2', direction: 'incoming', status: 'PENDING' },
    { ...invitation, id: '3', direction: 'outgoing', status: 'REJECTED' },
    { ...invitation, id: '4', direction: 'incoming', status: 'REJECTED', readTime: '2026-08-30' },
  ])

  const actions = rowActions()
  expect(actions.map((row) => row.map((action) => action.text()))).toEqual([
    ['打开知识库'],
    ['接受邀请', '拒绝邀请', '标为已读'],
    ['再次邀请'],
    [],
  ])
  expect(actions[0][0].attributes('href')).toBe(`/knowledge/${knowledgeId}`)
  expect(readKnowledgeInvitation).not.toHaveBeenCalled()
})

it('再次邀请原收件人并更新列表，保留长 ID 的精度', async () => {
  const rejected = { ...invitation, id: '1', direction: 'outgoing', status: 'REJECTED' }
  await mountMessages([rejected])
  listKnowledgeInvitations.mockResolvedValue([{ ...rejected, id: '2', status: 'PENDING' }])
  addMember.mockResolvedValue(true)

  await rowActions()[0][0].trigger('click')
  await flushPromises()

  expect(addMember).toHaveBeenCalledExactlyOnceWith(knowledgeId, { userId: targetUserId })
  expect(wrapper.get('.invitation-status').text()).toBe('待处理')
  expect(rowActions()[0]).toHaveLength(0)
})

it('已读、拒绝和接受操作各自使用所在行的邀请 ID', async () => {
  const pending = { ...invitation, direction: 'incoming', status: 'PENDING' }
  const rows = ['1', '2', '3'].map((id) => ({ ...pending, id }))
  const router = await mountMessages(rows)
  readKnowledgeInvitation.mockResolvedValue(true)
  rejectKnowledgeInvitation.mockResolvedValue(true)
  acceptKnowledgeInvitation.mockResolvedValue(knowledgeId)

  await rowActions()[0][2].trigger('click')
  await flushPromises()
  expect(readKnowledgeInvitation).toHaveBeenCalledExactlyOnceWith('1')
  expect(rowActions()[0].map((action) => action.text())).toEqual(['接受邀请', '拒绝邀请'])

  await rowActions()[1][1].trigger('click')
  await flushPromises()
  expect(rejectKnowledgeInvitation).toHaveBeenCalledExactlyOnceWith('2')

  await rowActions()[2][0].trigger('click')
  await flushPromises()
  expect(acceptKnowledgeInvitation).toHaveBeenCalledExactlyOnceWith('3')
  expect(router.currentRoute.value.params.id).toBe(knowledgeId)
})
