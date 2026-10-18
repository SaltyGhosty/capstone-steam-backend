/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// El backend corre en el 8081 (el 8080 lo ocupa otra app). En desarrollo, /api pasa por el
// proxy de Vite, así que el navegador solo habla con localhost:5173.
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
        // Para el navegador la petición es del mismo origen; si se reenvía su Origin, el CORS del
        // backend rechaza con 403 los POST/PUT cuando Vite no corre justo en el 5173.
        configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin')),
      },
      // Swagger UI y el JSON de OpenAPI viven fuera de /api. Sin changeOrigin, el redirect de
      // /swagger-ui.html a /swagger-ui/index.html vuelve al 5173 y no al puerto del backend.
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
