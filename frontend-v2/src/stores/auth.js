import { defineStore, acceptHMRUpdate } from 'pinia'
import { resetAuthExpiredNotice } from '../api/request'
import {
  login as apiLogin,
  register as apiRegister,
  logout as apiLogout,
  getCurrentUser,
} from '../api/auth'

/** 登录状态：token + 用户信息，localStorage 持久化（约定同老前端） */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
  }),
  getters: {
    isLogin: (s) => !!s.token,
    isAdmin: (s) => s.user?.userRole === 'admin',
    avatarText: (s) => (s.user?.userName || s.user?.userAccount || '?').charAt(0),
  },
  actions: {
    setSession(token, user) {
      resetAuthExpiredNotice()
      this.token = token
      this.user = user
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify(user))
    },
    async login(account, password) {
      const data = await apiLogin({ userAccount: account, userPassword: password })
      this.setSession(data.token, data.user)
    },
    async register(payload) {
      await apiRegister(payload)
    },
    async fetchCurrentUser() {
      this.user = await getCurrentUser()
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    async logout() {
      try {
        await apiLogout()
      } catch (e) {
        // 登出接口失败不阻断本地清理
      }
      this.clear()
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
  },
})

if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useAuthStore, import.meta.hot))
}
