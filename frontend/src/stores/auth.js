import { defineStore, acceptHMRUpdate } from 'pinia'
import { ROLE } from '../utils/roles'
import { resetAuthExpiredNotice } from '../api/request'
import {
  login as apiLogin,
  register as apiRegister,
  logout as apiLogout,
  getCurrentUser,
  updateProfile as apiUpdateProfile,
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
    isSuperAdmin: (state) => state.user?.userRole === ROLE.SUPER_ADMIN,
    isAdmin() {
      return this.isSuperAdmin || this.user?.userRole === ROLE.ADMIN
    },
  },
  actions: {
    setSession(token, user) {
      // 拿到新 token 即复位登录失效提醒闩锁，下次过期仍会提醒
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
    async register(account, password, checkPassword, userName, captchaId, captchaAnswer) {
      await apiRegister({ userAccount: account, userPassword: password, checkPassword, userName, captchaId, captchaAnswer })
    },
    async fetchCurrentUser() {
      const user = await getCurrentUser()
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },
    /** 更新个人资料（昵称/头像/简介），成功后刷新本地缓存的用户信息 */
    async updateProfile(data) {
      await apiUpdateProfile(data)
      await this.fetchCurrentUser()
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
