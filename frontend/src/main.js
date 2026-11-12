import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoadingDirective } from 'element-plus'
import {
  ArrowDown,
  ArrowRight,
  Bell,
  ChatDotRound,
  Collection,
  Compass,
  Delete,
  Document,
  Folder,
  Loading,
  Opportunity,
  Plus,
  Promotion,
  Setting,
  Star,
  User,
  View,
} from '@element-plus/icons-vue'

// Element Plus 按需引入（unplugin-vue-components，见 vite.config.js）。
// 插件只覆盖模板组件；以下三样必须手动补齐：
import 'element-plus/es/components/message/style/css'        // 函数式 ElMessage
import 'element-plus/es/components/message-box/style/css'    // 函数式 ElMessageBox
import 'element-plus/es/components/loading/style/css'        // v-loading 指令样式
import './style.css'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const pinia = createPinia()
const app = createApp(App)
app.use(pinia)
app.use(router)
// v-loading 指令（全量安装时由 app.use(ElementPlus) 附带）
app.directive('loading', ElLoadingDirective)
// 模板中直接使用的图标显式注册（:icon="Xxx" 绑定的图标在各组件内自行 import，可 tree-shake）
const icons = {
  ArrowDown, ArrowRight, Bell, ChatDotRound, Collection, Compass, Delete,
  Document, Folder, Loading, Opportunity, Plus, Promotion, Setting,
  Star, User, View,
}
for (const [name, component] of Object.entries(icons)) {
  app.component(name, component)
}
app.mount('#app')

// 全局登录失效事件：由 axios 响应拦截器触发，跳转登录页并带上当前页用于登录后回跳
// 必须同时清空 auth store：request.js 只删了 localStorage，Pinia 内存态仍持有旧 token，
// isLogin 依旧为 true，聊天会继续用旧 token 发请求，最终被静默成「（无回答）」。
window.addEventListener('auth:expired', () => {
  useAuthStore(pinia).clear()
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})
