import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 手机端独立 H5，默认 5174，代理到后端 8081
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
