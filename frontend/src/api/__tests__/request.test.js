import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 提前构造 axios mock，供 request.js 使用
const mocks = vi.hoisted(() => {
  const requestInterceptor = { use: vi.fn() }
  const responseInterceptor = { use: vi.fn() }
  const instance = {
    interceptors: { request: requestInterceptor, response: responseInterceptor },
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  }
  return { requestInterceptor, responseInterceptor, instance }
})

vi.mock('axios', () => ({
  default: { create: vi.fn(() => mocks.instance) },
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}))

import request, { resetAuthExpiredNotice } from '../request'
import { useAuthStore } from '../../stores/auth'

// 拦截器在模块加载时注册一次，这里取引用（不能用 beforeEach 取，会与 mock 清理冲突）
const onFulfilled = mocks.responseInterceptor.use.mock.calls[0][0]
const onRejected = mocks.responseInterceptor.use.mock.calls[0][1]
const onRequest = mocks.requestInterceptor.use.mock.calls[0][0]

describe('axios 请求封装', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('请求拦截器', () => {
    it('有 token 时自动携带 Authorization', () => {
      localStorage.setItem('token', 'jwt-token')
      const config = { headers: {} }
      onRequest(config)
      expect(config.headers.Authorization).toBe('Bearer jwt-token')
    })

    it('无 token 时不带 Authorization', () => {
      const config = { headers: {} }
      onRequest(config)
      expect(config.headers.Authorization).toBeUndefined()
    })
  })

  describe('响应拦截器', () => {
    it('code=0 直接返回 data', () => {
      const result = onFulfilled({ data: { code: 0, data: { id: 1 } } })
      expect(result).toEqual({ id: 1 })
    })

    it('code!=0 reject 并提示错误', async () => {
      await expect(onFulfilled({ data: { code: 40001, message: '账号已存在' } })).rejects.toThrow('账号已存在')
      const { ElMessage } = await import('element-plus')
      expect(ElMessage.error).toHaveBeenCalledWith('账号已存在')
    })

    it('40100 清除登录态并派发 auth:expired 事件', async () => {
      localStorage.setItem('token', 'expired-token')
      const handler = vi.fn()
      window.addEventListener('auth:expired', handler)

      await expect(onFulfilled({ data: { code: 40100, message: '未登录' } })).rejects.toThrow('未登录')

      expect(localStorage.getItem('token')).toBeNull()
      expect(localStorage.getItem('user')).toBeNull()
      expect(handler).toHaveBeenCalledTimes(1)
      window.removeEventListener('auth:expired', handler)
    })

    it('并发多个 40100 只弹一次提示、派发一次事件（去重）', async () => {
      resetAuthExpiredNotice()
      const handler = vi.fn()
      window.addEventListener('auth:expired', handler)
      localStorage.setItem('token', 'expired-token')

      // token 过期时页面常有多个并发请求同时返回 40100
      await Promise.all([
        expect(onFulfilled({ data: { code: 40100, message: '登录已失效' } })).rejects.toThrow('登录已失效'),
        expect(onFulfilled({ data: { code: 40100, message: '登录已失效' } })).rejects.toThrow('登录已失效'),
        expect(onFulfilled({ data: { code: 40100, message: '登录已失效' } })).rejects.toThrow('登录已失效'),
      ])

      const { ElMessage } = await import('element-plus')
      expect(ElMessage.error).toHaveBeenCalledTimes(1)
      expect(handler).toHaveBeenCalledTimes(1)
      window.removeEventListener('auth:expired', handler)
    })

    it('闩锁未复位时后续 40100 静默；登录成功（setSession）复位后可再次提醒', async () => {
      resetAuthExpiredNotice()
      localStorage.setItem('token', 'expired-token')
      const { ElMessage } = await import('element-plus')

      await expect(onFulfilled({ data: { code: 40100, message: '未登录' } })).rejects.toThrow('未登录')
      expect(ElMessage.error).toHaveBeenCalledTimes(1)

      // 未重新登录前的重复 40100：清登录态但不再弹
      await expect(onFulfilled({ data: { code: 40100, message: '未登录' } })).rejects.toThrow('未登录')
      expect(ElMessage.error).toHaveBeenCalledTimes(1)

      // 登录成功 → setSession 自动复位闩锁
      setActivePinia(createPinia())
      mocks.instance.post.mockResolvedValue({
        data: { code: 0, data: { token: 'new-token', user: { id: 1, userRole: 'user' } } },
      })
      const store = useAuthStore()
      await store.login('user1', 'pass12345')

      // 下次 token 过期恢复提醒
      localStorage.setItem('token', 'expired-again')
      await expect(onFulfilled({ data: { code: 40100, message: '未登录' } })).rejects.toThrow('未登录')
      expect(ElMessage.error).toHaveBeenCalledTimes(2)
    })

    it('HTTP 层错误（网络异常）提示并 reject', async () => {
      await expect(onRejected(new Error('Network Error'))).rejects.toThrow('Network Error')
    })
  })

  it('导出 axios 实例（baseURL=/api）', () => {
    expect(request).toBe(mocks.instance)
  })
})
