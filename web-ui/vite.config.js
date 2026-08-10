import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    // Allows Vite to accept requests forwarded through a tunnel (e.g. cloudflared/ngrok),
    // whose Host header won't match localhost. Only needed for temporary public sharing.
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})

