import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// No CDN fonts, no external assets, no analytics.
// The UI must load with the network disconnected (SC-013).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
})
