import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const devProxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      port: 3000,
      // Allows Vite to accept requests forwarded through a tunnel (e.g. cloudflared/ngrok),
      // whose Host header won't match localhost. Only needed for temporary public sharing.
      allowedHosts: true,
      proxy: {
        '/api': {
          target: devProxyTarget,
          changeOrigin: true
        }
      }
    }
  }
})

