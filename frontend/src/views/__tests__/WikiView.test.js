import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { getKnowledge, getWikiPage, listDocs, listWikiPages } from '../../api/knowledge'
import MainLayout from '../../layouts/MainLayout.vue'
import WikiView from '../WikiView.vue'

vi.mock('../../api/knowledge', async (importOriginal) => ({
  ...await importOriginal(),
  getKnowledge: vi.fn(),
  getWikiPage: vi.fn(),
  listDocs: vi.fn(),
  listWikiPages: vi.fn(),
}))

const knowledgeId = '2126146296831619075'
const [overview, annual, sick, failed, training] = [
  '休假制度', '带薪年假细则', '病假', '事假', '培训假',
].map((title, index) => ({
  id: `212614629683161908${index}`,
  title,
  sortOrder: index + 1,
  status: index === 3 ? 'FAILED' : 'SUCCESS',
}))
const overviewContent = [
  '[**年假**](#wiki-page-2)和病假均须提交申请。病假需附证明。',
  '[事假](#wiki-page-4) [无效目标](#wiki-page-99) [休假制度](#wiki-page-1)',
  '`培训假`及[培训假说明](https://example.com/leave)。',
  '## 来源\n\n培训假',
].join('\n\n')

let wrapper

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class {
    observe() {}
    disconnect() {}
  })
  getKnowledge.mockResolvedValue({ name: '员工手册' })
  listDocs.mockResolvedValue([])
  const pages = [overview, annual, sick, failed, training]
  listWikiPages.mockResolvedValue(pages)
  getWikiPage.mockImplementation(async (_, id) => ({
    ...pages.find((page) => page.id === id),
    content: id === overview.id ? overviewContent : '详见休假制度。',
  }))
})

afterEach(() => {
  wrapper?.unmount()
  vi.resetAllMocks()
  vi.unstubAllGlobals()
})

async function openWiki(url, embedded = false) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wiki', component: WikiView },
      { path: '/knowledge/:id/wiki', name: 'KnowledgeWiki', component: WikiView },
    ],
  })
  await router.push(url)
  wrapper = shallowMount(WikiView, {
    attachTo: document.body,
    props: embedded ? { knowledgeId } : {},
    global: { plugins: [router], directives: { loading: {} } },
  })
  await flushPromises()
  return router
}

it('正文链接打开同库章节，兼容旧正文、改名和失效目标', async () => {
  const router = await openWiki('/wiki', true)
  const links = wrapper.findAll('.md-body a[data-wiki-page]')
  expect(links.map((link) => link.text())).toEqual(['年假', '病假'])
  expect(links[0].attributes('href')).toBe(`/knowledge/${knowledgeId}/wiki?page=${annual.id}`)
  expect(wrapper.get('.md-body').text()).toContain('事假 无效目标 休假制度')
  expect(wrapper.get('.md-body code').text()).toBe('培训假')
  expect(wrapper.get('.md-body a[href^="https:"]').text()).toBe('培训假说明')

  await links[0].get('strong').trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.path).toBe('/wiki')
  expect(getWikiPage).toHaveBeenLastCalledWith(knowledgeId, annual.id)
  expect(wrapper.get('h3').text()).toBe(annual.title)
  expect(wrapper.get('[aria-current="true"] .page-title').text()).toBe(annual.title)
  expect(document.activeElement).toBe(wrapper.get('h3').element)
  expect(wrapper.get('.md-body a[data-wiki-page]').text()).toBe(overview.title)
})

it('章节直达链接可直接打开，正文跳转写入浏览器历史', async () => {
  const router = await openWiki(`/knowledge/${knowledgeId}/wiki?page=${annual.id}`)
  expect(wrapper.get('h3').text()).toBe(annual.title)

  await wrapper.get('.md-body a[data-wiki-page]').trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.query.page).toBe(overview.id)
  expect(wrapper.get('h3').text()).toBe(overview.title)

  router.back()
  await flushPromises()
  expect(wrapper.get('h3').text()).toBe(annual.title)
})

it('切换不同知识库的直达链接时读取新知识库', async () => {
  const otherKnowledgeId = '2126146296831619076'
  const otherPage = { ...overview, id: '2126146296831619090', title: '差旅制度' }
  listWikiPages.mockResolvedValueOnce([overview]).mockResolvedValueOnce([otherPage])
  getWikiPage.mockResolvedValueOnce(overview).mockResolvedValueOnce(otherPage)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/knowledge/:id/wiki', name: 'KnowledgeWiki', component: WikiView }],
  })
  await router.push(`/knowledge/${knowledgeId}/wiki?page=${overview.id}`)
  wrapper = mount(MainLayout, {
    global: {
      plugins: [router, createPinia()],
      directives: { loading: {} },
    },
  })
  await flushPromises()
  expect(wrapper.get('.page-content h3').text()).toBe(overview.title)

  await router.push(`/knowledge/${otherKnowledgeId}/wiki?page=${otherPage.id}`)
  await flushPromises()
  expect(getWikiPage).toHaveBeenLastCalledWith(otherKnowledgeId, otherPage.id)
  expect(wrapper.get('.page-content h3').text()).toBe(otherPage.title)
})

it('快速切换章节时忽略旧请求的迟到响应', async () => {
  await openWiki('/wiki', true)
  let finishAnnual
  getWikiPage.mockReturnValueOnce(new Promise((resolve) => { finishAnnual = resolve }))
  await wrapper.get(`[data-wiki-page="${annual.id}"]`).trigger('click')
  await wrapper.get(`[data-wiki-page="${sick.id}"]`).trigger('click')
  await flushPromises()

  finishAnnual(annual)
  await flushPromises()
  expect(wrapper.get('h3').text()).toBe(sick.title)
  expect(wrapper.get('[aria-current="true"] .page-title').text()).toBe(sick.title)
})
