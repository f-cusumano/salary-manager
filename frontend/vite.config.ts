/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In sviluppo le chiamate /api vanno al backend Spring Boot (porta 8080), salvo override con SAM_BACKEND_URL.
// Il backend avviato con mvnw ascolta solo su 127.0.0.1 (SAM_SERVER_ADDRESS): l'indirizzo IPv4 esplicito evita che
// "localhost" venga risolto prima in ::1.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backend = env.SAM_BACKEND_URL ? env.SAM_BACKEND_URL : 'http://127.0.0.1:8080';
  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target: backend, changeOrigin: true },
      },
    },
    preview: {
      port: 4173,
      proxy: {
        '/api': { target: backend, changeOrigin: true },
      },
    },
    build: {
      sourcemap: true,
      chunkSizeWarningLimit: 900,
    },
    test: {
      globals: true,
      environment: 'jsdom',
      // la suite end-to-end (e2e/, Playwright) non fa parte dei test unitari
      include: ['src/**/*.test.{ts,tsx}'],
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      restoreMocks: true,
      env: { TZ: 'Europe/Rome' },
    },
  };
});
