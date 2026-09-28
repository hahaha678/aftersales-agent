import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: env.BACKEND_URL || 'http://127.0.0.1:8080',
          changeOrigin: true,
          // Backend health is /actuator/health; business APIs retain /api.
          rewrite: (path) => path.replace(/^\/api\/system\/health(?=\?|$)/, '/actuator/health'),
        },
      },
    },
  }
})
