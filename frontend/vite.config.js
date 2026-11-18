import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    // '@/...' = 'src/...' (예: import { api } from '@/api/client')
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // ECharts(차트 라이브러리)가 커서 나오는 용량 경고 기준을 올림
    chunkSizeWarningLimit: 1500,
  },
  server: {
    // /api 로 시작하는 요청은 백엔드로 전달. 평소 로컬 개발은 localhost:8080,
    // Docker 안에서 띄울 때는 VITE_API_PROXY_TARGET으로 컨테이너 이름(backend:8080)을 넘겨받음
    proxy: {
      '/api': process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
    },
  },
})
