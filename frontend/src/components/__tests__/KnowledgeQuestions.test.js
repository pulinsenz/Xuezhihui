import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import KnowledgeQuestions from '../KnowledgeQuestions.vue'
import { listKnowledgeQuestions, saveKnowledgeQuestions } from '../../api/knowledge'

vi.mock('../../api/knowledge', () => ({ listKnowledgeQuestions: vi.fn(), saveKnowledgeQuestions: vi.fn() }))
let wrapper
beforeEach(() => {
  vi.resetAllMocks()
  listKnowledgeQuestions.mockResolvedValue(['如何考核？'])
})
afterEach(() => wrapper?.unmount())

const mount = async (isOwner = true) => {
  wrapper = shallowMount(KnowledgeQuestions, { props: { knowledgeId: '5', isOwner }, global: {
    directives: { loading: {} },
    stubs: {
      ElButton: { template: '<button><slot /></button>' },
      ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
      ElInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
    },
  } })
  await flushPromises()
}
const click = async (text) => wrapper.findAll('button').find((button) => button.text() === text).trigger('click')

it('只向作者显示编辑入口，其他可查看者仍能阅读问题', async () => {
  await mount(false)
  expect(wrapper.text()).toContain('如何考核？')
  expect(wrapper.text()).not.toContain('编辑常用问题')
})

it('重复/空问题阻止保存，修正后按顺序保存', async () => {
  await mount()
  await click('编辑常用问题')
  await click('添加问题')
  await click('保存常用问题')
  expect(saveKnowledgeQuestions).not.toHaveBeenCalled()
  expect(wrapper.find('[role="alert"]').text()).toContain('请填写')
  await wrapper.findAll('input')[1].setValue(' 如何考核？ ')
  await click('保存常用问题')
  expect(wrapper.find('[role="alert"]').text()).toContain('不能重复')
  await wrapper.findAll('input')[1].setValue(' 教材是什么？ ')
  await click('保存常用问题')
  await flushPromises()
  expect(saveKnowledgeQuestions).toHaveBeenCalledWith('5', ['如何考核？', '教材是什么？'])
  expect(wrapper.findAll('li')).toHaveLength(2)
})

it('加载失败提供重试，禁止用空数据覆盖已有问题', async () => {
  listKnowledgeQuestions.mockRejectedValueOnce(new Error('offline'))
  await mount()
  expect(wrapper.find('[role="alert"]').text()).toContain('加载失败')
  expect(wrapper.findAll('button').find((button) => button.text() === '编辑常用问题').attributes('disabled')).toBeDefined()
  await click('重新加载')
  await flushPromises()
  expect(wrapper.text()).toContain('如何考核？')
})
