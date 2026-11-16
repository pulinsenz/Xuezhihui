import { beforeEach, describe, expect, it, vi } from 'vitest'

// mock request 模块：只验证 api 函数的路径/参数拼装（方法 + url + options）
vi.mock('../request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import request from '../request'
import {
  deleteWiki,
  generateWiki,
  getWikiPage,
  listWikiPages,
  updateWikiPage,
} from '../knowledge'

describe('AI Wiki api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('generateWiki → POST /knowledge/{id}/wiki/generate', () => {
    generateWiki(7)
    expect(request.post).toHaveBeenCalledWith('/knowledge/7/wiki/generate')
  })

  it('listWikiPages → GET /knowledge/{id}/wiki', () => {
    listWikiPages(7)
    expect(request.get).toHaveBeenCalledWith('/knowledge/7/wiki')
  })

  it('getWikiPage → GET /knowledge/{id}/wiki/{pageId}', () => {
    getWikiPage(7, 42)
    expect(request.get).toHaveBeenCalledWith('/knowledge/7/wiki/42')
  })

  it('updateWikiPage → PUT 携带标题与正文', () => {
    updateWikiPage(7, 42, { title: 't', content: 'c' })
    expect(request.put).toHaveBeenCalledWith('/knowledge/7/wiki/42', { title: 't', content: 'c' })
  })

  it('deleteWiki → DELETE /knowledge/{id}/wiki', () => {
    deleteWiki(7)
    expect(request.delete).toHaveBeenCalledWith('/knowledge/7/wiki')
  })
})
