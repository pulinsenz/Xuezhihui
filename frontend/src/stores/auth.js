import { defineStore, acceptHMRUpdate } from 'pinia'
import {
  login as apiLogin,
  register as apiRegister,
  logout as apiLogout,
  getCurrentUser,
} from '../api/auth'

/**
 * 登录状态：token + 用户信息，localStorage 持久化
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
  }),
  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.user?.userRole === 'admin',
  },
  actions: {
    async login(account, password) {
      const data = await apiLogin({ userAccount: account, userPassword: password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    async register(account, password, checkPassword, userName) {
      await apiRegister({ userAccount: account, userPassword: password, checkPassword, userName })
    },
    async fetchCurrentUser() {
      const user = await getCurrentUser()
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
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

// 支持 HMR：热更新 store 定义
if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useAuthStore, import.meta.hot))
}
