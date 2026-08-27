import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../auth'
import * as authApi from '../../api/auth'

vi.mock('../../api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  getCurrentUser: vi.fn(),
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('login 成功保存 token 与用户信息并持久化', async () => {
    authApi.login.mockResolvedValue({
      token: 'token-1',
      user: { id: 1, userAccount: 'admin', userName: '管理员', userRole: 'admin' },
    })

    const store = useAuthStore()
    await store.login('admin', 'mock-password-1')

    expect(store.token).toBe('token-1')
    expect(store.isLogin).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(store.user.userAccount).toBe('admin')
    // localStorage 持久化，刷新后仍登录
    expect(localStorage.getItem('token')).toBe('token-1')
    expect(JSON.parse(localStorage.getItem('user')).userRole).toBe('admin')
  })

  it('login 普通用户 isAdmin 为 false', async () => {
    authApi.login.mockResolvedValue({
      token: 'token-2',
      user: { id: 2, userAccount: 'student', userRole: 'user' },
    })

    const store = useAuthStore()
    await store.login('student', 'pass12345')

    expect(store.isAdmin).toBe(false)
  })

  it('register 调用注册接口但不保存登录态', async () => {
    authApi.register.mockResolvedValue(1)
    const store = useAuthStore()
    await store.register('newuser', 'pass12345', 'pass12345', '新同学')

    expect(authApi.register).toHaveBeenCalledWith({
      userAccount: 'newuser',
      userPassword: 'pass12345',
      checkPassword: 'pass12345',
      userName: '新同学',
    })
    expect(store.isLogin).toBe(false)
  })

  it('logout 清空状态与 localStorage', async () => {
    authApi.logout.mockResolvedValue(true)
    localStorage.setItem('token', 'token-1')
    localStorage.setItem('user', JSON.stringify({ id: 1 }))

    const store = useAuthStore()
    await store.logout()

    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
  })

  it('clear 直接清空（无 token 场景兜底）', () => {
    localStorage.setItem('token', 'token-1')
    const store = useAuthStore()
    store.clear()
    expect(store.isLogin).toBe(false)
  })
})
