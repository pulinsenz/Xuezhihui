import { afterEach, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { routeLocationKey, routerKey } from 'vue-router'
import { getDocChunks, getKnowledge, getWikiPage, listDocs, listMyKnowledge, listWikiPages } from '../../api/knowledge'
import KnowledgeRetrievalTest from '../../components/KnowledgeRetrievalTest.vue'
import KnowledgeToolView from '../KnowledgeToolView.vue'

vi.mock('../../api/knowledge', async (importOriginal) => ({
  ...await importOriginal(),
  getDocChunks: vi.fn(),
  getKnowledge: vi.fn(),
  getWikiPage: vi.fn(),
  listDocs: vi.fn(),
  listMyKnowledge: vi.fn(),
  listWikiPages: vi.fn(),
}))

const firstId = '2126146296831619074'
const secondId = '2126146296831619075'
let wrapper

afterEach(() => {
  wrapper?.unmount()
  vi.resetAllMocks()
})

async function mountTool(tool) {
  listMyKnowledge.mockResolvedValue([
    { id: firstId, name: '课程资料' },
    { id: secondId, name: '实验记录' },
  ])
  wrapper = shallowMount(KnowledgeToolView, {
    props: { tool },
    global: {
      provide: {
        [routeLocationKey]: { params: {} },
        [routerKey]: { push: vi.fn() },
      },
      directives: { loading: {} },
      stubs: {
        RouterLink: true,
        KnowledgeChunks: false,
        KnowledgeToolDirectory: false,
        WikiView: false,
        ElButton: false,
        ElDrawer: {
          props: ['modelValue'],
          template: '<div v-if="modelValue" role="dialog"><slot /></div>',
        },
        ElInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)">',
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

it('切换知识库时重建检索表单，保留知识库 ID 的精度', async () => {
  await mountTool('retrieval')
  const initialForm = wrapper.getComponent(KnowledgeRetrievalTest)
  expect(initialForm.props('knowledgeId')).toBe(firstId)

  await wrapper.get('input[type="search"]').setValue(' 实验 ')
  expect(wrapper.findAll('.tool-library-list button').map((button) => button.text())).toEqual(['实验记录'])
  expect(wrapper.getComponent(KnowledgeRetrievalTest).element).toBe(initialForm.element)
  await wrapper.get('.tool-library-list button').trigger('click')
  const nextForm = wrapper.getComponent(KnowledgeRetrievalTest)
  expect(nextForm.props('knowledgeId')).toBe(secondId)
  expect(nextForm.element).not.toBe(initialForm.element)
  expect(wrapper.get('.tool-work h2').text()).toBe('实验记录')

  await wrapper.get('input[type="search"]').setValue('不存在的知识库')
  expect(wrapper.get('.tool-library').text()).toContain('没有匹配的知识库')
  expect(wrapper.getComponent(KnowledgeRetrievalTest).element).toBe(nextForm.element)
})

it('在知识库下展开 Wiki 章节，搜索并切换正文', async () => {
  const pages = [
    { id: '41', title: '实验概览', sortOrder: 1, status: 'SUCCESS' },
    { id: '42', title: '准备设备', sortOrder: 2, status: 'SUCCESS' },
  ]
  getKnowledge.mockResolvedValue({ name: '实验记录', isOwner: true })
  listDocs.mockResolvedValue([])
  listWikiPages.mockImplementation(async (id) => id === secondId ? pages : [])
  getWikiPage.mockImplementation(async (_, id) => ({
    ...pages.find((page) => page.id === id), content: '这是章节 ' + id,
  }))
  await mountTool('wiki')
  await wrapper.findAll('.tool-library-list > li > button')[1].trigger('click')
  await vi.dynamicImportSettled()
  await flushPromises()
  expect(listWikiPages).toHaveBeenCalledWith(secondId)
  expect(wrapper.get('.wiki-page h2').text()).toContain('实验记录')
  expect(wrapper.findComponent(KnowledgeRetrievalTest).exists()).toBe(false)
  expect(wrapper.find('.wiki-sidebar').exists()).toBe(false)
  expect(wrapper.get('.tool-library-list > li > [aria-current="true"]').attributes('aria-expanded')).toBe('true')
  expect(wrapper.findAll('.tool-branch .chapter-list button')).toHaveLength(2)

  await wrapper.get('.tool-library-list > li > [aria-current="true"]').trigger('click')
  expect(wrapper.find('.tool-branch').exists()).toBe(false)
  expect(wrapper.get('.md-body').text()).toBe('这是章节 41')
  await wrapper.get('input[type="search"]').setValue('设备')
  expect(wrapper.findAll('.tool-branch .chapter-list button')).toHaveLength(1)
  await wrapper.get('.tool-branch .chapter-list button').trigger('click')
  await flushPromises()
  expect(wrapper.get('.tool-branch [aria-current="true"]').text()).toContain('准备设备')
  expect(wrapper.get('.md-body').text()).toBe('这是章节 42')
})

it('按文档分页查看切片，切换文档后忽略旧请求的迟到响应', async () => {
  const firstDoc = '2126146296831619084'
  const secondDoc = '2126146296831619085'
  const docs = [
    { id: firstDoc, name: '课程大纲.md' },
    { id: secondDoc, name: '实验步骤.txt' },
  ]
  listDocs.mockImplementation(async (id) => id === secondId ? docs : [])
  let resolveFirstDoc
  getDocChunks.mockReturnValueOnce(new Promise((resolve) => { resolveFirstDoc = resolve }))
  getDocChunks.mockResolvedValueOnce(Array.from({ length: 21 }, (_, index) => `实验正文 ${index + 1}`))

  await mountTool('chunks')
  await wrapper.findAll('.tool-library-list > li > button')[1].trigger('click')
  await flushPromises()
  expect(listDocs).toHaveBeenCalledWith(secondId, 0, 'vectorized')

  expect(getDocChunks).toHaveBeenCalledWith(secondId, firstDoc)
  expect(wrapper.text()).toContain('正在读取切片')
  expect(wrapper.find('.tool-work .doc-list').exists()).toBe(false)
  await wrapper.findAll('.tool-branch .doc-list button')[1].trigger('click')
  await flushPromises()
  expect(getDocChunks).toHaveBeenCalledWith(secondId, secondDoc)
  expect(wrapper.findAll('.chunk')).toHaveLength(20)
  expect(wrapper.get('.doc-list button[aria-pressed="true"]').text()).toContain('21 条')
  expect(wrapper.get('.chunk-number').text()).toBe('切片 1')

  resolveFirstDoc(['课程大纲正文'])
  await flushPromises()
  expect(wrapper.text()).not.toContain('课程大纲正文')
  await wrapper.get('nav button').trigger('click')
  expect(wrapper.findAll('.chunk')).toHaveLength(1)
  expect(wrapper.get('.chunk-number').text()).toBe('切片 21')
  expect(wrapper.get('.chunk p').text()).toBe('实验正文 21')

  await wrapper.get('.tool-mobile-directory button').trigger('click')
  await wrapper.get('[role="dialog"] input').setValue('实验步骤')
  expect(wrapper.findAll('[role="dialog"] .doc-list button')).toHaveLength(1)
  await wrapper.get('[role="dialog"] .doc-list button').trigger('click')
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  expect(wrapper.get('.tool-work h3').text()).toBe('实验步骤.txt')
})
