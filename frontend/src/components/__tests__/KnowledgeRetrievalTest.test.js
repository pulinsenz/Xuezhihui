import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import KnowledgeRetrievalTest from '../KnowledgeRetrievalTest.vue'
import { testKnowledgeRetrieval } from '../../api/knowledge'

vi.mock('../../api/knowledge', () => ({ testKnowledgeRetrieval: vi.fn() }))

let wrapper
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class {
    observe() {}
    disconnect() {}
  })
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
  vi.resetAllMocks()
})

function mountRetrieval() {
  wrapper = shallowMount(KnowledgeRetrievalTest, {
    attachTo: document.body,
    props: { knowledgeId: '12' },
    global: {
      stubs: {
        ElButton: false,
        ElEmpty: true,
        ElInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
      },
    },
  })
}

it('提交当前库的问题，展示同文不同来源及其排名', async () => {
  testKnowledgeRetrieval.mockResolvedValue({
    elapsedMs: 14,
    hits: [
      { chunkId: '100', docName: '现行办法.pdf', text: '绩点达到 3.8', bm25Rank: 1, fusionScore: 0.0164 },
      { chunkId: '200', docName: '历史办法.pdf', text: '绩点达到 3.8', vectorRank: 2, fusionScore: 0.0161 },
    ],
  })
  mountRetrieval()
  await wrapper.get('form').trigger('submit')
  expect(testKnowledgeRetrieval).not.toHaveBeenCalled()

  await wrapper.get('textarea').setValue('  奖学金条件  ')
  await wrapper.get('form').trigger('submit')
  await flushPromises()

  expect(testKnowledgeRetrieval).toHaveBeenCalledWith('12', '奖学金条件')
  expect(wrapper.get('[role="status"]').text()).toContain('命中 2 条')
  expect(wrapper.findAll('li').map((hit) => hit.get('h3').text())).toEqual(['现行办法.pdf', '历史办法.pdf'])
  expect(wrapper.text()).toContain('关键词第 1 名')
  expect(wrapper.text()).toContain('向量第 2 名')
})

it('请求失败时保留问题并显示可重试提示', async () => {
  testKnowledgeRetrieval.mockRejectedValue(new Error('offline'))
  mountRetrieval()
  await wrapper.get('textarea').setValue('选课')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('请稍后重试')
  expect(wrapper.get('textarea').element.value).toBe('选课')
})

it('检索中保留提交按钮的焦点，并阻止重复提交', async () => {
  let finish
  testKnowledgeRetrieval.mockReturnValue(new Promise((resolve) => { finish = resolve }))
  mountRetrieval()
  await wrapper.get('textarea').setValue('选课')
  const button = wrapper.get('button[type="submit"]')
  button.element.focus()
  await wrapper.get('form').trigger('submit')

  expect(button.element.disabled).toBe(false)
  expect(button.attributes('aria-disabled')).toBe('true')
  expect(document.activeElement).toBe(button.element)
  await wrapper.get('form').trigger('submit')
  expect(testKnowledgeRetrieval).toHaveBeenCalledTimes(1)

  finish({ elapsedMs: 1, hits: [] })
  await flushPromises()
  expect(button.attributes('aria-disabled')).toBe('false')
  expect(document.activeElement).toBe(button.element)
})
