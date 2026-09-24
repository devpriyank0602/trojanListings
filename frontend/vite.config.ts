import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// No CDN fonts, no external assets, no analytics.
// The UI must load with the network disconnected (SC-013).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Vite's terminal doesn't log proxied calls by default. This makes every
        // /api request the browser makes visible in the `npm run dev` terminal,
        // alongside the backend's own access log for the same request.
        configure(proxy) {
          proxy.on('proxyReq', (_proxyReq, req) => {
            const started = Date.now()
            ;(req as unknown as { _loggedAt: number })._loggedAt = started
            console.log(`\x1b[36m→ ${req.method} ${req.url}\x1b[0m`)
          })
          proxy.on('proxyRes', (proxyRes, req) => {
            const started = (req as unknown as { _loggedAt?: number })._loggedAt
            const ms = started ? Date.now() - started : '?'
            const color = (proxyRes.statusCode ?? 0) >= 400 ? '\x1b[33m' : '\x1b[32m'
            console.log(`${color}← ${req.method} ${req.url} -> ${proxyRes.statusCode} (${ms} ms)\x1b[0m`)
          })
          proxy.on('error', (err, req) => {
            console.log(`\x1b[31m✗ ${req.method} ${req.url} -> ${err.message}\x1b[0m`)
          })
        },
      },
    },
  },
})
