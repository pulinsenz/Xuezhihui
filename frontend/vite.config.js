import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：模板组件自动 import + 样式（替代全量 use(ElementPlus)），
    // 首屏 JS 从 ~850KB 大幅缩减；函数式组件（ElMessage/ElMessageBox）与 v-loading
    // 指令的样式/注册在 main.js 手动补齐
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
  server: {
    port: 5173,
    proxy: {
      // 开发环境转发到后端（context-path 为 /api），同源绕开 CORS
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    // element-plus 的 ES 入口 import 了 theme-chalk css；external 加载时 Node 原生
    // ESM 拒绝 .css 扩展（Unknown file extension）。inline 后走 vite 管道，
    // css 由插件消化，vi.mock('element-plus') 也能稳定拦截
    server: {
      deps: {
        inline: ['element-plus'],
      },
    },
  },
})
