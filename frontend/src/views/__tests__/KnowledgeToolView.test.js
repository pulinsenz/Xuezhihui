import { afterEach, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { routeLocationKey, routerKey } from 'vue-router'
import { getKnowledge, listDocs, listMyKnowledge, listWikiPages } from '../../api/knowledge'
import KnowledgeRetrievalTest from '../../components/KnowledgeRetrievalTest.vue'
import KnowledgeToolView from '../KnowledgeToolView.vue'

vi.mock('../../api/knowledge', async (importOriginal) => ({
  ...await importOriginal(),
  getKnowledge: vi.fn(),
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
        WikiView: false,
        ElSelect: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
        },
        ElOption: {
          props: ['label', 'value'],
          template: '<option :value="value">{{ label }}</option>',
        },
      },
    },
  })
  await flushPromises()
}

it('切换知识库时重建检索表单，保留知识库 ID 的精度', async () => {
  await mountTool('retrieval')
  expect(wrapper.findComponent(KnowledgeRetrievalTest).exists()).toBe(false)
  await wrapper.get('select').setValue(firstId)
  const initialForm = wrapper.getComponent(KnowledgeRetrievalTest)
  expect(initialForm.props('knowledgeId')).toBe(firstId)

  await wrapper.get('select').setValue(secondId)
  const nextForm = wrapper.getComponent(KnowledgeRetrievalTest)
  expect(nextForm.props('knowledgeId')).toBe(secondId)
  expect(nextForm.vm).not.toBe(initialForm.vm)
})

it('在 AI Wiki 页面加载选中知识库的 Wiki', async () => {
  getKnowledge.mockResolvedValue({ name: '实验记录', isOwner: true })
  listDocs.mockResolvedValue([])
  listWikiPages.mockResolvedValue([])
  await mountTool('wiki')
  await wrapper.get('select').setValue(secondId)
  await vi.dynamicImportSettled()
  await flushPromises()
  expect(listWikiPages).toHaveBeenCalledWith(secondId)
  expect(wrapper.get('h2').text()).toBe('AI Wiki · 实验记录')
  expect(wrapper.findComponent(KnowledgeRetrievalTest).exists()).toBe(false)
})
