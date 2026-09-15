import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ElMessage } from 'element-plus'
import router from '../index'
import { useAuthStore } from '../../stores/auth'

// 守卫里会弹 ElMessage，jsdom 下只 mock 掉弹层避免 DOM 噪音
vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

const loginAs = (role) => {
  localStorage.setItem('token', 'token-1')
  localStorage.setItem('user', JSON.stringify({ id: 1, userAccount: 'tester', userRole: role }))
  return useAuthStore()
}

describe('路由守卫：未登录统一跳转登录页', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('未登录访问受保护页面 → 重定向 /login 并携带 redirect', async () => {
    await router.push('/dashboard')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard')
    expect(ElMessage.warning).toHaveBeenCalledWith('请先登录')
  })

  it('未登录深链（含参数）→ redirect 保留完整路径', async () => {
    await router.push('/knowledge/7?tab=docs')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/knowledge/7?tab=docs')
  })

  it('未登录访问 /login → 正常放行', async () => {
    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('已登录访问 /login → 跳转 /dashboard', async () => {
    loginAs('user')
    await router.push('/profile')
    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('普通用户访问管理员页面 → 拦回 /dashboard', async () => {
    loginAs('user')
    await router.push('/profile')
    await router.push('/admin/users')

    expect(router.currentRoute.value.path).toBe('/dashboard')
    expect(ElMessage.error).toHaveBeenCalledWith('无权限访问该页面')
  })

  it('管理员访问管理员页面 → 放行', async () => {
    loginAs('admin')
    await router.push('/profile')
    await router.push('/admin/users')

    expect(router.currentRoute.value.path).toBe('/admin/users')
  })
})
