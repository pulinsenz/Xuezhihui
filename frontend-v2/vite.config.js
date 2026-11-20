import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// frontend-v2：全新主题化前端（青薄荷/薇紫/墨夜），老 frontend 保持不动
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      // 与老前端一致：后端 context-path 为 /api，同源转发绕开 CORS
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
      },
    },
  },
})
