import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backend = process.env.CODEARENA_BACKEND ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': backend,
      '/ws': { target: backend.replace(/^http/, 'ws'), ws: true },
    },
  },
});
