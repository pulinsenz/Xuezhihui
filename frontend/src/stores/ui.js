import { defineStore } from 'pinia'

/**
 * 全局 UI 状态：登录弹窗等。
 * 登录弹窗挂载于 MainLayout，但聊天页等子页面也要能打开它，故放到 store 共享。
 */
export const useUiStore = defineStore('ui', {
  state: () => ({
    loginDialogVisible: false,
  }),
  actions: {
    openLogin() {
      this.loginDialogVisible = true
    },
    closeLogin() {
      this.loginDialogVisible = false
    },
  },
})
