import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './style.css'

const pinia = createPinia()
const app = createApp(App)
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
// 注册全部图标组件，模板中可直接 <el-icon><Folder /></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
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
