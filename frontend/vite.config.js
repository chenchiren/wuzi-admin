import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        // 大依赖单独分包，避免单 3.9MB 主包拖慢首屏
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'axios'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
          'vendor-charts': ['echarts'],
          'vendor-naive': ['naive-ui']
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
