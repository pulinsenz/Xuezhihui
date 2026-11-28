import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import request from '../../api/request'
import { deleteDoc, updateKnowledge, updateWikiPage } from '../../api/knowledge'
import { useAuthStore } from '../../stores/auth'
import appRouter from '../index'

vi.mock('../../api/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  resetAuthExpiredNotice: vi.fn(),
}))

const knowledgeId = '2126146296831619075'
const docId = '2126146296831619084'
const knowledge = { id: knowledgeId, name: '实验记录', isOwner: true }
const otherKnowledge = { id: '2126146296831619076', name: '其他资料', isOwner: true }
const wikiPages = [
  { id: '41', sortOrder: 1, title: '概览', status: 'SUCCESS' },
  { id: '42', sortOrder: 2, title: '实验步骤', status: 'SUCCESS' },
]
const docsPath = `/knowledge/${knowledgeId}/docs`
const chunksPath = `${docsPath}/${docId}/chunks`
const wikiPath = `/knowledge/${knowledgeId}/wiki`
let wrapper
let router
let auth
let docs
let wikiContent
let publicName

const requestCount = (path) => request.get.mock.calls.filter(([url]) => url === path).length

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class {
    observe() {}
    unobserve() {}
    disconnect() {}
  })
  docs = [{ id: docId, name: '课程实验.md' }]
  wikiContent = '第一次实验'
  publicName = '材料实验'
  request.get.mockImplementation(async (path, config) => {
    switch (path) {
      case '/auth/me': return auth.user
      case '/knowledge/invitations':
      case '/chat/sessions': return []
      case '/knowledge/list': return [knowledge, otherKnowledge]
      case '/knowledge/public/list': return [{ ...knowledge, name: config.params.keyword ? publicName : '全部资料' }]
      case `/knowledge/${knowledgeId}`: return knowledge
      case `/knowledge/${otherKnowledge.id}/docs`: return []
      case docsPath: return docs
      case chunksPath: return Array.from({ length: 21 }, (_, index) => `切片正文 ${index + 1}`)
      case wikiPath: return wikiPages
      case `${wikiPath}/41`: return { ...wikiPages[0], content: '概览正文' }
      case `${wikiPath}/42`: return { ...wikiPages[1], content: wikiContent }
      default: throw new Error(`Unexpected request: ${path}`)
    }
  })
})

afterEach(() => {
  wrapper?.unmount()
  localStorage.clear()
  vi.resetAllMocks()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

async function open(path) {
  const pinia = createPinia()
  auth = useAuthStore(pinia)
  auth.setSession('token-1', { id: '1', userName: '测试用户', userRole: 'user' })
  router = createRouter({ history: createMemoryHistory(), routes: appRouter.options.routes })
  await router.push(path)
  wrapper = mount({ render: () => h(RouterView) }, {
    global: {
      plugins: [pinia, router],
      directives: { loading: {} },
      stubs: {
        ElDropdown: true,
        ElTooltip: { template: '<span><slot /></span>' },
        ElDialog: {
          props: ['modelValue', 'title'],
          template: '<div v-if="modelValue" role="dialog" :aria-label="title"><slot /></div>',
        },
        ElPagination: {
          emits: ['update:currentPage'],
          template: '<button @click="$emit(\'update:currentPage\', 2)">第 2 页</button>',
        },
      },
    },
  })
  await flushPromises()
}

async function go(path) {
  await router.push(path)
  await flushPromises()
}

it('切换知识工具页保留切片页码和检索输入，删除文档后清理旧切片', async () => {
  await open('/vector-chunks')
  await wrapper.get('nav button').trigger('click')
  expect(wrapper.get('.chunk-number').text()).toBe('切片 21')

  await go('/retrieval-test')
  await wrapper.findAll('.tool-library-list button')[1].trigger('click')
  await wrapper.get('textarea').setValue('实验如何复现？')
  request.post.mockResolvedValueOnce({
    hits: [{ chunkId: '1', docName: '实验手册.md', text: '按记录核对实验参数', fusionScore: 0.01 }],
    elapsedMs: 2,
  })
  await wrapper.get('.retrieval-form').trigger('submit')
  await flushPromises()
  await go('/vector-chunks')
  expect(wrapper.get('.doc-list [aria-pressed="true"] .tool-row-name').text()).toBe('课程实验.md')
  expect(wrapper.get('.chunk-number').text()).toBe('切片 21')
  expect(requestCount(docsPath)).toBe(1)
  expect(requestCount(chunksPath)).toBe(1)

  await go('/retrieval-test')
  expect(wrapper.get('.tool-library-list [aria-pressed="true"]').text()).toBe('其他资料')
  expect(wrapper.get('textarea').element.value).toBe('实验如何复现？')
  expect(wrapper.get('.retrieval-hit').text()).toContain('实验手册.md')
  expect(request.post).toHaveBeenCalledExactlyOnceWith(`/knowledge/${otherKnowledge.id}/retrieval-test`, { query: '实验如何复现？' })
  docs = []
  await deleteDoc(knowledgeId, docId)
  await go('/vector-chunks')
  expect(requestCount(docsPath)).toBe(2)
  expect(wrapper.find('.chunk').exists()).toBe(false)
  expect(wrapper.text()).toContain('暂无已入库的文档')
})

it('过期后后台刷新沿用已提交的搜索词，保留最新的输入', async () => {
  const now = vi.spyOn(Date, 'now').mockReturnValue(100_000)
  await open('/public-knowledge')
  const search = () => wrapper.get('.public-kb-page input')
  await search().setValue('材料')
  await search().trigger('keyup.enter')
  await flushPromises()
  await search().setValue('还没提交的查询')
  await go('/knowledge')
  now.mockReturnValue(160_000)
  let resolveRefresh
  request.get.mockImplementationOnce(() => new Promise((resolve) => { resolveRefresh = resolve }))
  await go('/public-knowledge')
  expect(search().element.value).toBe('还没提交的查询')
  expect(wrapper.get('.kb-name').text()).toBe('材料实验')
  expect(request.get).toHaveBeenLastCalledWith('/knowledge/public/list', { params: { keyword: '材料' } })
  resolveRefresh([{ ...knowledge, name: '更新后的材料实验' }])
  await flushPromises()
  expect(wrapper.get('.kb-name').text()).toBe('更新后的材料实验')
})

it('应用内修改会刷新保留的 Wiki，并保持当前章节', async () => {
  await open('/wiki')
  await vi.dynamicImportSettled()
  await flushPromises()
  await wrapper.findAll('.page-item')[1].trigger('click')
  await flushPromises()
  await go('/knowledge')
  await go('/wiki')
  expect(wrapper.get('.md-body').text()).toBe('第一次实验')
  expect(requestCount(wikiPath)).toBe(1)

  await go('/knowledge')
  wikiContent = '修改后的实验说明'
  await updateWikiPage(knowledgeId, '42', { content: wikiContent })
  await go('/wiki')
  expect(requestCount(wikiPath)).toBe(2)
  expect(wrapper.get('[aria-current="true"] .page-title').text()).toBe('实验步骤')
  expect(wrapper.get('.md-body').text()).toBe('修改后的实验说明')
})

it('仪表盘刷新已修改的知识库，快捷入口仍能在保留页打开新建弹窗', async () => {
  await open('/dashboard')
  await go('/knowledge')
  await updateKnowledge({ id: knowledgeId, name: '新名称' })
  await go('/dashboard')
  expect(requestCount('/knowledge/list')).toBe(3)
  const shortcut = wrapper.findAll('.shortcut-btn').find((button) => button.text().includes('新建专业知识库'))
  await shortcut.trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/knowledge')
  expect(wrapper.find('[role="dialog"][aria-label="新建知识库"]').exists()).toBe(true)
  expect(requestCount('/knowledge/list')).toBe(4)
})

it('切换账号后丢弃前一个账号的页面状态', async () => {
  await open('/vector-chunks')
  await wrapper.findAll('.tool-library-list button')[1].trigger('click')
  await wrapper.get('.tool-search input').setValue('其他')
  expect(wrapper.get('.tool-library-list [aria-pressed="true"]').text()).toBe('其他资料')
  await go('/knowledge')
  auth.setSession('token-2', { id: '2', userName: '另一个用户', userRole: 'user' })
  await go('/vector-chunks')
  expect(wrapper.get('.tool-search input').element.value).toBe('')
  expect(wrapper.get('.tool-library-list [aria-pressed="true"]').text()).toBe('实验记录')
})
