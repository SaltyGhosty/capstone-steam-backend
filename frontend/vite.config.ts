/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// Il backend gira sull'8081 (l'8080 è occupato da un'altra app). In sviluppo, /api passa dal
// proxy di Vite, quindi il browser parla solo con localhost:5173.
const apiTarget = process.env.API_TARGET || 'http://localhost:8081'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        // Per il browser la richiesta è della stessa origin; se si inoltra il suo Origin, il CORS del
        // backend rifiuta con 403 i POST/PUT quando Vite non gira esattamente sul 5173.
        configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin')),
      },
      // Swagger UI e il JSON di OpenAPI stanno fuori da /api. Senza changeOrigin, il redirect di
      // /swagger-ui.html a /swagger-ui/index.html torna al 5173 e non alla porta del backend.
      '/swagger-ui': { target: apiTarget },
      '/v3/api-docs': { target: apiTarget },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
